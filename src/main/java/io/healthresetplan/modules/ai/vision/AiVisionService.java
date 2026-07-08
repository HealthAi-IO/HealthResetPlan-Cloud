package io.healthresetplan.modules.ai.vision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiVisionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024L;

    private final OneApiService oneApiService;

    public AiVisionService(OneApiService oneApiService) {
        this.oneApiService = oneApiService;
    }

    public Map<String, Object> analyze(String userId, MultipartFile file, String type) {
        String normalizedType = normalizeType(type);
        validate(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(50001, "图片读取失败");
        }

        String mimeType = file.getContentType();
        String raw = oneApiService.analyzeImage(
                userId,
                Base64.getEncoder().encodeToString(bytes),
                mimeType,
                prompt(normalizedType)
        );

        Map<String, Object> result = parseResult(raw, normalizedType);
        result.put("type", normalizedType);
        result.put("provider", oneApiService.visionProviderLabel());
        return result;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "图片不能为空");
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_TYPES.contains(mimeType.toLowerCase())) {
            throw new BusinessException(40001, "仅支持 JPEG / PNG / WebP / GIF 图片");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(40001, "图片大小不能超过 10MB");
        }
    }

    private String normalizeType(String type) {
        if ("skin".equals(type) || "tongue".equals(type) || "hair".equals(type)) {
            return type;
        }
        throw new BusinessException(40001, "不支持的识别类型");
    }

    private String prompt(String type) {
        if ("skin".equals(type)) {
            return """
                    You are an AI skin assessment assistant. Analyze the uploaded front-face or local skin image.
                    Return strict JSON only, no markdown:
                    {
                      "summary": "中文总结，40字以内",
                      "skinType": "干性|油性|混合性|中性|敏感倾向|无法判断",
                      "skinTone": "肤色观察，如偏白/自然/偏黄/偏红/暗沉/无法判断",
                      "healthScore": 0-100,
                      "dimensions": [
                        {
                          "name": "肤质/出油",
                          "score": 0-100,
                          "status": "良好|轻度关注|需要关注|无法判断",
                          "detail": "结合图片说明可见表现",
                          "suggestion": "具体护理建议"
                        },
                        {
                          "name": "肤色/暗沉",
                          "score": 0-100,
                          "status": "良好|轻度关注|需要关注|无法判断",
                          "detail": "结合图片说明可见表现",
                          "suggestion": "具体护理建议"
                        },
                        {
                          "name": "痘痘/粉刺",
                          "score": 0-100,
                          "status": "良好|轻度关注|需要关注|无法判断",
                          "detail": "说明可见痘痘、闭口或炎症情况",
                          "suggestion": "具体护理建议"
                        },
                        {
                          "name": "毛孔/黑头",
                          "score": 0-100,
                          "status": "良好|轻度关注|需要关注|无法判断",
                          "detail": "说明毛孔粗大、黑头可见程度",
                          "suggestion": "具体护理建议"
                        },
                        {
                          "name": "纹理/细纹",
                          "score": 0-100,
                          "status": "良好|轻度关注|需要关注|无法判断",
                          "detail": "说明皮肤纹理、干纹或细纹",
                          "suggestion": "具体护理建议"
                        },
                        {
                          "name": "泛红/敏感",
                          "score": 0-100,
                          "status": "良好|轻度关注|需要关注|无法判断",
                          "detail": "说明泛红、屏障不稳或敏感倾向",
                          "suggestion": "具体护理建议"
                        }
                      ],
                      "observations": ["3-6条可见观察"],
                      "careRoutine": ["早间护理建议", "晚间护理建议", "防晒/清洁/保湿建议"],
                      "advice": "详细中文综合建议。必须说明 AI 结果仅供健康管理和护肤参考，不能替代医生诊断。",
                      "riskLevel": "low|medium|high",
                      "rawText": "中文详细说明"
                    }
                    Rules:
                    1. Be detailed and practical, but do not diagnose disease.
                    2. If the face is not clear, set uncertain dimensions to 无法判断 and explain why.
                    3. Do not invent medical diagnoses. Mention seeing a dermatologist for severe acne, rapid worsening, pain, bleeding, infection, or persistent abnormal signs.
                    """;
        }

        String target = switch (type) {
            case "tongue" -> "tongue coating and tongue appearance";
            case "hair" -> "hairline, scalp visibility, and possible hair-loss signs";
            default -> "visible skin signs";
        };
        return """
                Analyze the uploaded image for %s. Return strict JSON only:
                {
                  "summary": "Chinese summary within 40 chars",
                  "observations": ["visible observation in Chinese"],
                  "advice": "Chinese care suggestions. Must say this is AI reference only and cannot replace a doctor.",
                  "riskLevel": "low|medium|high",
                  "rawText": "short Chinese detail"
                }
                Rules: do not diagnose disease; only describe visible signs and health-care suggestions; no markdown.
                """.formatted(target);
    }

    private Map<String, Object> parseResult(String raw, String type) {
        String json = extractJsonObject(raw);
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<>() {});
            return normalize(parsed, raw, type);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("summary", "已完成 AI 图像分析");
            fallback.put("observations", List.of());
            fallback.put("advice", "以下内容仅供健康管理参考，AI 不能替代医生诊断；如有明显不适或持续异常，请及时咨询医生。");
            fallback.put("riskLevel", "low");
            fallback.put("rawText", raw == null ? "" : raw);
            if ("skin".equals(type)) {
                fallback.put("skinType", "无法判断");
                fallback.put("skinTone", "无法判断");
                fallback.put("dimensions", List.of());
                fallback.put("careRoutine", List.of());
            }
            return fallback;
        }
    }

    private Map<String, Object> normalize(Map<String, Object> parsed, String raw, String type) {
        Map<String, Object> result = new LinkedHashMap<>(parsed);
        result.putIfAbsent("summary", "已完成 AI 图像分析");
        result.putIfAbsent("observations", List.of());
        result.putIfAbsent("riskLevel", "low");
        result.putIfAbsent("rawText", raw == null ? "" : raw);
        if ("skin".equals(type)) {
            result.putIfAbsent("skinType", "无法判断");
            result.putIfAbsent("skinTone", "无法判断");
            result.putIfAbsent("dimensions", List.of());
            result.putIfAbsent("careRoutine", List.of());
        }
        String advice = String.valueOf(result.getOrDefault("advice", ""));
        if (advice.isBlank()) {
            advice = "以下内容仅供健康管理参考，AI 不能替代医生诊断；如有明显不适或持续异常，请及时咨询医生。";
        } else if (!advice.contains("不能替代医生") && !advice.contains("不能代替医生")) {
            advice = advice + " 以上内容仅供健康管理参考，AI 不能替代医生诊断。";
        }
        result.put("advice", advice);
        return result;
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
}
