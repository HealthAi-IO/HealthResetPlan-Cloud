package io.healthresetplan.modules.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.MedicalRiskGuard;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.report.dto.AnalyzeResponse;
import io.healthresetplan.modules.report.dto.ReportSaveRequest;
import io.healthresetplan.modules.report.entity.HealthReport;
import io.healthresetplan.modules.report.mapper.HealthReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024L;

    private static final String OCR_PROMPT = """
            你是一位医疗检验数据解析专家。请阅读用户上传的体检、检验或检查报告图片，
            尽可能完整地抽取结构化健康指标。

            只输出严格 JSON，不要输出 Markdown、代码块或额外解释。JSON 格式：
            {
              "reportDate": "YYYY-MM-DD 或 null",
              "indicators": [
                {
                  "category": "血糖|血脂|血压|肝功能|肾功能|血常规|甲状腺|尿常规|心电图|影像|其他",
                  "name": "指标名称",
                  "value": "检测值或结论原文",
                  "unit": "单位，没有则为空字符串",
                  "referenceRange": "参考范围原文，没有则为空字符串",
                  "status": "normal|high|low|unknown"
                }
              ],
              "summary": "一句话总结本次报告重点，80 字以内",
              "analysisAdvice": "基于报告内容给出简短分析和建议，必须说明 AI 不能代替医生诊断，只提供健康管理建议",
              "rawText": "图片中识别到的主要文字"
            }

            要求：
            1. 逐行提取图片里所有医学指标、检查项目和影像结论，不只提取异常项。
            2. 每个指标必须独立放入 indicators 数组，保留原始指标名、数值、单位和参考范围。
            3. 若没有具体医学指标，indicators 返回空数组，但 rawText 必须保留报告可见内容。
            4. 无论是否有指标，都必须生成 analysisAdvice。
            5. 若报告日期无法识别，reportDate 填 null。
            6. status 根据参考范围判断；无法判断时填 unknown。
            """;

    private static final String OCR_FAST_PROMPT = """
            Read the uploaded medical/lab report image and return strict JSON only.
            Schema:
            {
              "reportDate": "YYYY-MM-DD or null",
              "sourceRowCount": "number of visible medical indicator rows",
              "indicators": [
                {
                  "category": "blood_sugar|blood_lipid|blood_pressure|liver|kidney|cbc|thyroid|urine|ecg|imaging|other",
                  "name": "original item name",
                  "value": "original value or conclusion",
                  "unit": "unit or empty string",
                  "referenceRange": "reference range or empty string",
                  "status": "normal|high|low|unknown"
                }
              ],
              "summary": "short Chinese summary within 40 chars",
              "analysisAdvice": "short Chinese analysis and advice; must say AI cannot replace a doctor and is only for health suggestions",
              "rawText": "non-indicator conclusion text only, or empty string"
            }
            Rules: no markdown; count every visible medical indicator row first; extract every row exactly once and preserve
            the original name, value, unit and reference range; sourceRowCount must equal indicators.length; never guess
            unreadable values; always generate analysisAdvice; no diagnosis.
            """;

    private static final String OCR_RETRY_SUFFIX = """

            The previous response was incomplete. Re-read the whole image row by row. Return one valid JSON object only.
            sourceRowCount must exactly equal indicators.length. Do not omit any visible indicator and do not repeat rows.
            """;

    private final HealthReportMapper reportMapper;
    private final OneApiService oneApiService;
    private final AiUsageLimiter usageLimiter;

    public ReportService(HealthReportMapper reportMapper, OneApiService oneApiService,
                         AiUsageLimiter usageLimiter) {
        this.reportMapper = reportMapper;
        this.oneApiService = oneApiService;
        this.usageLimiter = usageLimiter;
    }

    public AnalyzeResponse analyze(MultipartFile file, String userId) {
        long startedAt = System.currentTimeMillis();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "图片不能为空");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_TYPES.contains(mimeType.toLowerCase())) {
            throw new BusinessException(40001, "仅支持 JPEG / PNG / WebP / GIF 格式图片");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(40001, "图片大小不能超过 10MB");
        }
        log.info("Report OCR accepted mimeType={} sizeBytes={}", mimeType, file.getSize());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(50001, "图片读取失败");
        }

        String base64 = Base64.getEncoder().encodeToString(bytes);
        usageLimiter.consume(userId, AiUsageLimiter.Type.REPORT);
        try {
            OneApiService.VisionCompletion completion = oneApiService.analyzeImage(
                    userId, base64, mimeType, OCR_FAST_PROMPT);
            log.info("Report OCR finished elapsedMs={} rawLength={}",
                    System.currentTimeMillis() - startedAt,
                    completion.content().length());
            AnalyzeResponse result = tryParseCompleteResult(completion.content(), completion.label());
            if (result != null) {
                return result;
            }

            log.warn("Report OCR result incomplete, retrying once");
            OneApiService.VisionCompletion retry = oneApiService.analyzeImage(
                    userId, base64, mimeType, OCR_FAST_PROMPT + OCR_RETRY_SUFFIX);
            result = tryParseCompleteResult(retry.content(), retry.label());
            if (result != null) {
                return result;
            }
            throw new BusinessException(50301, "未能完整识别全部指标，请上传清晰原图或分段拍摄后重试");
        } catch (RuntimeException e) {
            usageLimiter.release(userId, AiUsageLimiter.Type.REPORT);
            throw e;
        }
    }

    @Transactional
    public void save(ReportSaveRequest req, String userId) {
        HealthReport existing = reportMapper.selectOne(new LambdaQueryWrapper<HealthReport>()
                .eq(HealthReport::getUserId, userId)
                .eq(HealthReport::getClientId, req.getClientId()));

        if (existing != null) {
            fillFields(existing, req);
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setServerUpdatedAt(LocalDateTime.now());
            reportMapper.updateById(existing);
            return;
        }

        HealthReport report = new HealthReport();
        report.setUserId(userId);
        report.setClientId(req.getClientId());
        fillFields(report, req);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        report.setServerUpdatedAt(LocalDateTime.now());
        reportMapper.insert(report);
    }

    public List<HealthReport> list(String userId, int page, int size) {
        Page<HealthReport> pager = new Page<>(page, size);
        return reportMapper.selectPage(pager, new LambdaQueryWrapper<HealthReport>()
                        .eq(HealthReport::getUserId, userId)
                        .orderByDesc(HealthReport::getReportTime))
                .getRecords();
    }

    @Transactional
    public void delete(String clientId, String userId) {
        HealthReport report = reportMapper.selectOne(new LambdaQueryWrapper<HealthReport>()
                .eq(HealthReport::getUserId, userId)
                .eq(HealthReport::getClientId, clientId));
        if (report == null) {
            throw new BusinessException(40401, "报告不存在");
        }
        reportMapper.deleteById(report.getId());
    }

    private AnalyzeResponse tryParseCompleteResult(String rawJson, String provider) {
        String json = extractJsonObject(rawJson);

        try {
            AnalyzeResponse response = MAPPER.readValue(json, AnalyzeResponse.class);
            response = normalizeAnalyzeResponse(response, provider);
            if (!hasAllIndicators(response)) {
                log.warn("LLM OCR result count mismatch sourceRowCount={} indicatorCount={}",
                        response.getSourceRowCount(), response.getIndicators().size());
                return null;
            }
            return response;
        } catch (Exception e) {
            log.warn("LLM OCR JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    static boolean hasAllIndicators(AnalyzeResponse response) {
        return response.getSourceRowCount() > 0
                && response.getIndicators() != null
                && response.getSourceRowCount() == response.getIndicators().size();
    }

    private String extractJsonObject(String raw) {
        String json = raw == null ? "" : raw.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start > 0 && end > start) {
                json = json.substring(start + 1, end).trim();
            }
        }
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return json.substring(first, last + 1).trim();
        }
        return json;
    }

    private AnalyzeResponse normalizeAnalyzeResponse(AnalyzeResponse response, String provider) {
        response.setProvider(provider);
        if (response.getIndicators() == null) {
            response.setIndicators(List.of());
        }
        String advice = response.getAnalysisAdvice();
        if (advice == null || advice.isBlank()) {
            advice = "AI 已根据报告内容生成初步分析建议。AI 不能代替医生诊断，只提供健康管理建议；如有异常结果、不适症状或用药调整需求，请及时咨询医生。";
        } else if (!advice.contains("不能代替医生") && !advice.contains("不代替医生")) {
            advice = advice.trim() + " AI 不能代替医生诊断，只提供健康管理建议；如有异常结果、不适症状或用药调整需求，请及时咨询医生。";
        }
        response.setAnalysisAdvice(advice);
        String riskText = String.join(" ", nullToEmpty(response.getRawText()), nullToEmpty(response.getSummary()), advice);
        if (MedicalRiskGuard.safetyReply(riskText) != null || hasCriticalIndicator(response)) {
            response.setHighRisk(true);
            response.setRiskMessage("报告存在需要优先就医核实的风险信息，请尽快联系医疗机构；AI 不提供治疗、用药或剂量建议。");
            response.setAnalysisAdvice(response.getRiskMessage());
        }
        return response;
    }

    private boolean hasCriticalIndicator(AnalyzeResponse response) {
        for (AnalyzeResponse.Indicator item : response.getIndicators()) {
            String text = (nullToEmpty(item.getName()) + " " + nullToEmpty(item.getValue())).toLowerCase();
            double value = firstNumber(item.getValue());
            if ((text.contains("血压") && value >= 180) || (text.contains("血糖") && (value >= 16.7 || value <= 3.0))) return true;
        }
        return false;
    }

    private double firstNumber(String value) {
        if (value == null) return Double.NaN;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\\\d+(?:\\\\.\\\\d+)?").matcher(value);
        return matcher.find() ? Double.parseDouble(matcher.group()) : Double.NaN;
    }

    private void fillFields(HealthReport report, ReportSaveRequest req) {
        if (req.getReportTime() != null && !req.getReportTime().isBlank()) {
            try {
                report.setReportTime(LocalDateTime.parse(req.getReportTime()));
            } catch (Exception ignored) {
                report.setReportTime(LocalDateTime.now());
            }
        } else if (report.getReportTime() == null) {
            report.setReportTime(LocalDateTime.now());
        }

        if (req.getClientUpdatedAt() != null && !req.getClientUpdatedAt().isBlank()) {
            try {
                report.setClientUpdatedAt(LocalDateTime.parse(req.getClientUpdatedAt()));
            } catch (Exception ignored) {
                report.setClientUpdatedAt(LocalDateTime.now());
            }
        } else {
            report.setClientUpdatedAt(LocalDateTime.now());
        }

        report.setDeviceId(nullToEmpty(req.getDeviceId()));
        report.setImageOssKey(nullToEmpty(req.getImageOssKey()));
        report.setImageWrappedDek(req.getImageWrappedDek());
        report.setImageDekIv(req.getImageDekIv());
        report.setImageDekTag(req.getImageDekTag());
        report.setOcrTextCipher(req.getOcrTextCipher());
        report.setOcrTextIv(req.getOcrTextIv());
        report.setOcrTextTag(req.getOcrTextTag());
        report.setStructuredCipher(req.getStructuredCipher());
        report.setStructuredIv(req.getStructuredIv());
        report.setStructuredTag(req.getStructuredTag());
        report.setSummaryCipher(req.getSummaryCipher());
        report.setSummaryIv(req.getSummaryIv());
        report.setSummaryTag(req.getSummaryTag());
        report.setAlg(req.getAlg() != null && !req.getAlg().isBlank() ? req.getAlg() : "aes-256-gcm:v1");
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
