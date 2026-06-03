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
    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024L; // 10MB

    private static final String OCR_PROMPT = """
            你是专业医学报告解析专家。请仔细阅读图片中的体检报告，提取所有健康指标数据。
            严格按照以下 JSON 格式输出，不要包含任何代码块标记（```）或额外说明：

            {
              "reportDate": "YYYY-MM-DD（若无法识别则填 null）",
              "indicators": [
                {
                  "category": "血糖|血脂|血压|肝功能|肾功能|血常规|甲状腺|尿常规|心电图|其他",
                  "name": "指标名称",
                  "value": "检测值",
                  "unit": "单位",
                  "referenceRange": "参考范围原始文本",
                  "status": "normal|high|low|unknown"
                }
              ],
              "summary": "一句话总结本次体检重点（50字以内）",
              "rawText": "图片中识别到的所有文字"
            }

            注意：
            1. 只输出纯 JSON，不要加任何前缀或后缀
            2. status 根据参考范围判断：normal=在正常范围，high=偏高，low=偏低，unknown=无参考范围或无法判断
            3. 若识别不到报告日期则 reportDate 填 null
            """;

    private final HealthReportMapper reportMapper;
    private final OneApiService oneApiService;

    public ReportService(HealthReportMapper reportMapper, OneApiService oneApiService) {
        this.reportMapper = reportMapper;
        this.oneApiService = oneApiService;
    }

    // ── 图像分析（不入库，返回明文结构化结果给客户端确认）────────────

    public AnalyzeResponse analyze(MultipartFile file) {
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
        // userId 传 null：OCR 属于一次性操作，不计入每日 AI 配额
        String rawJson = oneApiService.analyzeImage(null, base64, mimeType, OCR_PROMPT);

        return parseAnalyzeResult(rawJson, "oneapi");
    }

    // ── 保存确认后的报告（客户端加密数据原样存储）────────────────────

    @Transactional
    public void save(ReportSaveRequest req, String userId) {
        HealthReport existing = reportMapper.selectOne(new LambdaQueryWrapper<HealthReport>()
                .eq(HealthReport::getUserId, userId)
                .eq(HealthReport::getClientId, req.getClientId()));

        if (existing != null) {
            // 幂等更新
            fillFields(existing, req);
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setServerUpdatedAt(LocalDateTime.now());
            reportMapper.updateById(existing);
        } else {
            HealthReport report = new HealthReport();
            report.setUserId(userId);
            report.setClientId(req.getClientId());
            fillFields(report, req);
            report.setCreatedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            report.setServerUpdatedAt(LocalDateTime.now());
            reportMapper.insert(report);
        }
    }

    // ── 列表（仅返回元数据，加密内容由客户端解密）────────────────────

    public List<HealthReport> list(String userId, int page, int size) {
        Page<HealthReport> pager = new Page<>(page, size);
        return reportMapper.selectPage(pager, new LambdaQueryWrapper<HealthReport>()
                        .eq(HealthReport::getUserId, userId)
                        .orderByDesc(HealthReport::getReportTime))
                .getRecords();
    }

    // ── 删除（软删）──────────────────────────────────────────────────

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

    // ── 内部 ──────────────────────────────────────────────────────────

    private AnalyzeResponse parseAnalyzeResult(String rawJson, String provider) {
        // 尝试去掉 LLM 可能残留的 markdown 代码块标记
        String json = rawJson.trim();
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
            log.warn("LLM 返回的 JSON 解析失败，降级为 rawText 模式: {}", e.getMessage());
            AnalyzeResponse fallback = new AnalyzeResponse();
            fallback.setRawText(rawJson);
            fallback.setProvider(provider);
            return fallback;
        }
    }

    private void fillFields(HealthReport r, ReportSaveRequest req) {
        if (req.getReportTime() != null) {
            try {
                r.setReportTime(LocalDateTime.parse(req.getReportTime()));
            } catch (Exception ignored) {}
        }
        r.setDeviceId(nullToEmpty(req.getDeviceId()));
        if (req.getClientUpdatedAt() != null) {
            try {
                r.setClientUpdatedAt(LocalDateTime.parse(req.getClientUpdatedAt()));
            } catch (Exception ignored) {}
        }
        r.setImageOssKey(nullToEmpty(req.getImageOssKey()));
        r.setImageWrappedDek(req.getImageWrappedDek());
        r.setImageDekIv(req.getImageDekIv());
        r.setImageDekTag(req.getImageDekTag());
        r.setOcrTextCipher(req.getOcrTextCipher());
        r.setOcrTextIv(req.getOcrTextIv());
        r.setOcrTextTag(req.getOcrTextTag());
        r.setStructuredCipher(req.getStructuredCipher());
        r.setStructuredIv(req.getStructuredIv());
        r.setStructuredTag(req.getStructuredTag());
        r.setSummaryCipher(req.getSummaryCipher());
        r.setSummaryIv(req.getSummaryIv());
        r.setSummaryTag(req.getSummaryTag());
        r.setAlg(req.getAlg() != null ? req.getAlg() : "aes-256-gcm:v1");
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
