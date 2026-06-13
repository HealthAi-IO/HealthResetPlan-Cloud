package io.healthresetplan.modules.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
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
              "rawText": "图片中识别到的主要文字"
            }

            要求：
            1. 逐行提取图片里所有医学指标、检查项目和影像结论，不只提取异常项。
            2. 每个指标必须独立放入 indicators 数组，保留原始指标名、数值、单位和参考范围。
            3. 若报告日期无法识别，reportDate 填 null。
            4. status 根据参考范围判断；无法判断时填 unknown。
            """;

    private final HealthReportMapper reportMapper;
    private final OneApiService oneApiService;

    public ReportService(HealthReportMapper reportMapper, OneApiService oneApiService) {
        this.reportMapper = reportMapper;
        this.oneApiService = oneApiService;
    }

    public AnalyzeResponse analyze(MultipartFile file) {
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

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(50001, "图片读取失败");
        }

        String base64 = Base64.getEncoder().encodeToString(bytes);
        String rawJson = oneApiService.analyzeImage(null, base64, mimeType, OCR_PROMPT);
        return parseAnalyzeResult(rawJson, oneApiService.visionProviderLabel());
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

    private AnalyzeResponse parseAnalyzeResult(String rawJson, String provider) {
        String json = rawJson == null ? "" : rawJson.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start > 0 && end > start) {
                json = json.substring(start + 1, end).trim();
            }
        }

        try {
            AnalyzeResponse response = MAPPER.readValue(json, AnalyzeResponse.class);
            response.setProvider(provider);
            return response;
        } catch (Exception e) {
            log.warn("LLM OCR JSON parse failed, falling back to raw text: {}", e.getMessage());
            AnalyzeResponse fallback = new AnalyzeResponse();
            fallback.setRawText(rawJson);
            fallback.setSummary("报告已识别，请人工核对原文");
            fallback.setProvider(provider);
            return fallback;
        }
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
