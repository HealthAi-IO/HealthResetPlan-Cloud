package io.healthresetplan.modules.ai.vision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import io.healthresetplan.modules.files.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiVisionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024L;

    private final OneApiService oneApiService;
    private final AiUsageLimiter usageLimiter;
    private final MembershipService membershipService;
    private final FileStorageService fileStorageService;

    public AiVisionService(OneApiService oneApiService, AiUsageLimiter usageLimiter,
                           MembershipService membershipService,
                           FileStorageService fileStorageService) {
        this.oneApiService = oneApiService;
        this.usageLimiter = usageLimiter;
        this.membershipService = membershipService;
        this.fileStorageService = fileStorageService;
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

        return analyzeBytes(userId, bytes, file.getContentType(), normalizedType);
    }

    public Map<String, Object> analyzeStored(
            String userId, String objectKey, String mimeType, String type) {
        String normalizedType = normalizeType(type);
        validateMimeType(mimeType);
        byte[] bytes = fileStorageService.read(objectKey, userId);
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(40001, "图片不存在或已删除");
        }
        if (bytes.length > MAX_SIZE_BYTES) {
            throw new BusinessException(40001, "图片大小不能超过 10MB");
        }
        return analyzeBytes(userId, bytes, mimeType, normalizedType);
    }

    private Map<String, Object> analyzeBytes(
            String userId, byte[] bytes, String mimeType, String normalizedType) {
        String feature = "meal".equals(normalizedType)
                ? "meal_analysis"
                : "ai_vision_" + normalizedType;
        membershipService.requireCredit(userId, feature);
        usageLimiter.consume(userId, AiUsageLimiter.Type.IMAGE);
        try {
            OneApiService.VisionCompletion completion = oneApiService.analyzeImage(
                    userId,
                    Base64.getEncoder().encodeToString(bytes),
                    mimeType,
                    prompt(normalizedType)
            );

            Map<String, Object> result = parseResult(completion.content(), normalizedType);
            result.put("type", normalizedType);
            result.put("provider", completion.label());
            if (membershipService.billingEnabled()
                    && !membershipService.consume(userId, feature)) {
                throw new BusinessException(42903, "AI 健康权益已用完，请充值后继续使用");
            }
            return result;
        } catch (RuntimeException e) {
            usageLimiter.release(userId, AiUsageLimiter.Type.IMAGE);
            throw e;
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "图片不能为空");
        }
        validateMimeType(file.getContentType());
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(40001, "图片大小不能超过 10MB");
        }
    }

    private void validateMimeType(String mimeType) {
        if (mimeType == null || !ALLOWED_TYPES.contains(mimeType.toLowerCase())) {
            throw new BusinessException(40001, "仅支持 JPEG / PNG / WebP / GIF 图片");
        }
    }

    private String normalizeType(String type) {
        if ("skin".equals(type) || "tongue".equals(type) || "hair".equals(type) || "meal".equals(type)) {
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
                      "observations": [
                        "画面视觉现象 + 通俗皮肤生理解释，4-6条"
                      ],
                      "adviceSections": {
                        "饮食调理": ["2-4条可执行建议"],
                        "生活作息 / 皮肤护理": ["2-4条可执行建议"],
                        "就医预警": ["2-4条需要皮肤科就诊的情况"]
                      },
                      "careRoutine": ["早间护理建议", "晚间护理建议", "防晒/清洁/保湿建议"],
                      "advice": "一句话总建议。结尾必须包含双重免责警示。",
                      "riskLevel": "low|medium|high",
                      "rawText": "中文详细说明"
                    }
                    Rules:
                    1. Use the fixed structure: short conclusion, observations, adviceSections.
                    2. observations must have 4-6 items. Each item must include visible detail plus plain skin physiology.
                    3. adviceSections must be practical and specific, not generic.
                    4. Be detailed and practical, but do not diagnose disease.
                    2. If the face is not clear, set uncertain dimensions to 无法判断 and explain why.
                    3. Do not invent medical diagnoses. Mention seeing a dermatologist for severe acne, rapid worsening, pain, bleeding, infection, or persistent abnormal signs.
                    4. The advice must end with: AI 视觉识别仅作日常健康参考，不能替代中医师 / 皮肤科医生线下专业诊断、开药；身体不适或脱发持续加重请前往正规医院就诊。
                    """;
        }
        if ("meal".equals(type)) {
            return """
                    你是餐食热量识别助手。请分析上传的餐食照片。
                    只返回一个严格 JSON 对象，不要 Markdown，不要解释文字，不要代码块：
                    {
                      "summary": "中文总结，40字以内，例如：已识别午餐餐食",
                      "mealName": "根据图片真实食物命名，不要使用示例名称",
                      "totalCalories": 0,
                      "proteinG": 0,
                      "carbsG": 0,
                      "fatG": 0,
                      "healthScore": 0,
                      "glycemicLoad": 0,
                      "foods": [
                        {"name": "图片中可见食材名称", "weightG": 0, "calories": 0}
                      ],
                      "nutrition": {
                        "proteinG": 0,
                        "carbsG": 0,
                        "fiberG": 0,
                        "sugarG": 0,
                        "fatG": 0,
                        "saturatedFatG": 0,
                        "monounsaturatedFatG": 0,
                        "polyunsaturatedFatG": 0,
                        "transFatG": 0,
                        "cholesterolMg": 0
                      },
                      "advice": "中文饮食建议，说明识别结果为估算值。",
                      "riskLevel": "low|medium|high",
                      "rawText": "中文详细说明"
                    }
                    规则：
                    1. 画面里只要有可食用物体，就必须识别；单个水果、零食、饮料、点心也算餐食。
                    2. 如果食物被手拿着、旁边有人脸或背景复杂，请忽略人物和背景，只估算可见食物本身。
                    3. foods 必须是数组，至少列出 1 个画面中可见食材；不要因为不是完整餐盘就返回空数组。
                    4. 每个食材必须包含英文键 name、weightG、calories，数值只写数字。
                    5. totalCalories 必须等于 foods.calories 的合理汇总估算；proteinG、carbsG、fatG 必须给数字。
                    6. 如果图片不清晰，也要基于可见内容给保守估算，并在 advice 说明“估算值，需手动校准”。
                    7. 只有完全没有可见食物时，才允许 foods 为空，并在 advice 说明没有可识别食物。
                    8. 不要诊断疾病，不要推荐药物。
                    9. healthScore 必须按以下固定 100 分规则评分：营养搭配 30 分、食材质量 25 分、蔬菜与膳食纤维 20 分、烹饪方式 15 分、热量与份量 10 分。天然食物、优质蛋白、蔬菜和粗粮加分；油炸、明显重油、高糖饮料和高度加工食品扣分。
                    10. 评分只能依据图片中可见并可合理估算的内容；无法判断盐、油或重量时应保守评分，并在 advice 中注明“评分基于图片估算，仅供参考”。
                    """;
        }

        if ("tongue".equals(type)) {
            return """
                    你是日常舌象健康参考助手。请分析上传的舌苔照片，只返回严格 JSON，不要 Markdown：
                    {
                      "summary": "简短核心结论标题，40字以内",
                      "observations": [
                        "画面视觉现象 + 通俗中医/生理原理解释，必须 4-6 条"
                      ],
                      "adviceSections": {
                        "健脾祛湿饮食推荐 / 忌口": ["3-5条可执行饮食建议"],
                        "睡眠运动祛湿方案": ["3-5条可执行作息、运动、祛湿方案"],
                        "必须看中医的症状预警清单": ["3-5条就医预警"]
                      },
                      "advice": "一句话总建议，结尾必须包含双重免责警示。",
                      "riskLevel": "low|medium|high",
                      "rawText": "中文详细说明"
                    }
                    生成要求：
                    1. 若舌象类似“舌淡红润、薄白苔、舌体略胖”，可见观察需扩充到 6 条，并结合脾虚水湿、运化能力、齿痕/胖大舌、苔薄白等通俗原理解释。
                    2. 后续遇到厚腻苔、裂纹舌、舌红、舌尖红、苔黄、苔少等任意舌象，也必须套用同样分层结构细化。
                    3. observations 每条必须是“画面看到了什么 + 这可能提示什么机理”，不能只写结论。
                    4. adviceSections 必须给可落地方案，避免“注意饮食、规律作息”等空话。
                    5. 不诊断疾病、不建议用药方。必须提示中医师线下辨证。
                    6. advice 结尾必须包含：AI 视觉识别仅作日常健康参考，不能替代中医师 / 皮肤科医生线下专业诊断、开药；身体不适或脱发持续加重请前往正规医院就诊。
                    """;
        }
        if ("hair".equals(type)) {
            return """
                    你是日常头皮/脱发视觉参考助手。请分析上传的发际线、头顶或分缝照片，只返回严格 JSON，不要 Markdown：
                    {
                      "summary": "简短核心结论标题，40字以内",
                      "observations": [
                        "画面视觉现象 + 通俗毛囊/脱发生理原理解释，必须 4-6 条"
                      ],
                      "adviceSections": {
                        "养发饮食营养方案": ["3-5条可执行营养建议"],
                        "头皮洗护 + 作息减压养护": ["3-5条洗护、作息、减压建议"],
                        "需要皮肤科就诊的脱发加重判定标准": ["3-5条就医预警"]
                      },
                      "advice": "一句话总建议，结尾必须包含双重免责警示。",
                      "riskLevel": "low|medium|high",
                      "rawText": "中文详细说明"
                    }
                    生成要求：
                    1. 若画面类似“发际线居中，头顶局部稀疏可见头皮”，可见观察需扩充到 5 条，并结合毛囊微小化、发干变细、头皮暴露、雄激素相关脱发/休止期脱发/脂溢性脱发等通俗原理解释。
                    2. 后续遇到斑秃、发际线后移、M 型发际线、头顶稀疏、头皮油腻红痒脱屑等，也必须套用同样分层结构细化。
                    3. observations 每条必须是“画面看到了什么 + 这可能对应什么脱发机理”，不能只写结论。
                    4. adviceSections 必须给可落地方案，包含蛋白质、铁、锌、维生素D/B族、洗发频率、避免牵拉、睡眠压力管理等。
                    5. 不诊断疾病、不建议处方药。必须提示皮肤科线下评估。
                    6. advice 结尾必须包含：AI 视觉识别仅作日常健康参考，不能替代中医师 / 皮肤科医生线下专业诊断、开药；身体不适或脱发持续加重请前往正规医院就诊。
                    """;
        }
        String target = "visible skin signs";
        return """
                Analyze the uploaded image for %s. Return strict JSON only:
                {
                  "summary": "Chinese summary within 40 chars",
                  "observations": ["visible observation in Chinese, 4-6 items"],
                  "adviceSections": {
                    "饮食调理": ["actionable advice"],
                    "生活作息 / 护理": ["actionable advice"],
                    "就医预警": ["doctor warning signs"]
                  },
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
            fallback.put("observations", defaultObservations(type));
            fallback.put("adviceSections", defaultAdviceSections(type));
            fallback.put("advice", disclaimer());
            fallback.put("riskLevel", "low");
            fallback.put("rawText", raw == null ? "" : raw);
            if ("skin".equals(type)) {
                fallback.put("skinType", "无法判断");
                fallback.put("skinTone", "无法判断");
                fallback.put("dimensions", List.of());
                fallback.put("careRoutine", List.of());
            } else if ("meal".equals(type)) {
                fallback.put("mealName", "识别餐食");
                fallback.put("totalCalories", caloriesFromRaw(raw));
                fallback.put("proteinG", 0);
                fallback.put("carbsG", 0);
                fallback.put("fatG", 0);
                fallback.put("healthScore", 0);
                fallback.put("glycemicLoad", 0);
                fallback.put("foods", List.of());
                fallback.put("nutrition", Map.of());
                normalizeMeal(fallback, raw);
            }
            return fallback;
        }
    }

    private Map<String, Object> normalize(Map<String, Object> parsed, String raw, String type) {
        Map<String, Object> result = new LinkedHashMap<>(parsed);
        result.putIfAbsent("summary", "已完成 AI 图像分析");
        result.putIfAbsent("observations", defaultObservations(type));
        result.putIfAbsent("adviceSections", defaultAdviceSections(type));
        result.putIfAbsent("riskLevel", "low");
        result.putIfAbsent("rawText", raw == null ? "" : raw);
        if ("skin".equals(type)) {
            result.putIfAbsent("skinType", "无法判断");
            result.putIfAbsent("skinTone", "无法判断");
            result.putIfAbsent("dimensions", List.of());
            result.putIfAbsent("careRoutine", List.of());
        } else if ("meal".equals(type)) {
            normalizeMeal(result, raw);
            result.putIfAbsent("mealName", "未命名餐单");
            result.putIfAbsent("totalCalories", 0);
            result.putIfAbsent("proteinG", 0);
            result.putIfAbsent("carbsG", 0);
            result.putIfAbsent("fatG", 0);
            result.putIfAbsent("healthScore", 0);
            result.putIfAbsent("glycemicLoad", 0);
            result.putIfAbsent("foods", List.of());
            result.putIfAbsent("nutrition", Map.of());
        }
        String advice = String.valueOf(result.getOrDefault("advice", ""));
        if (advice.isBlank()) {
            advice = disclaimer();
        } else if (!advice.contains("不能替代") && !advice.contains("不能代替")) {
            advice = advice + " " + disclaimer();
        }
        result.put("advice", advice);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void normalizeMeal(Map<String, Object> result, String raw) {
        Object foods = firstPresent(result, "foods", "ingredients", "items", "foodItems", "食材", "食材列表", "成分");
        if (foods instanceof List<?> list) {
            result.put("foods", list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> normalizeMealFood((Map<Object, Object>) item))
                    .filter(item -> !String.valueOf(item.get("name")).isBlank())
                    .toList());
        }

        Object nutrition = firstPresent(result, "nutrition", "营养", "营养素", "nutrients");
        if (nutrition instanceof Map<?, ?> map) {
            result.put("nutrition", map);
        }

        result.put("mealName", stringValue(firstPresent(result, "mealName", "name", "title", "餐单名称", "餐食名称"), "未命名餐单"));
        result.put("totalCalories", numberValue(firstPresent(result, "totalCalories", "calories", "kcal", "总热量", "热量"), caloriesFromRaw(raw)));
        result.put("proteinG", numberValue(firstPresent(result, "proteinG", "protein", "proteinGram", "蛋白质"), 0));
        result.put("carbsG", numberValue(firstPresent(result, "carbsG", "carbs", "carbohydrate", "carbohydrates", "碳水", "碳水化合物"), 0));
        result.put("fatG", numberValue(firstPresent(result, "fatG", "fat", "脂肪"), 0));
        double healthScore = numberValue(firstPresent(result, "healthScore", "score", "健康评分"), 0);
        result.put("healthScore", Math.max(0, Math.min(100, healthScore)));
        result.put("glycemicLoad", numberValue(firstPresent(result, "glycemicLoad", "gl", "血糖负荷"), 0));

        Object normalizedFoods = result.get("foods");
        if (!(normalizedFoods instanceof List<?> list) || list.isEmpty()) {
            double calories = numberValue(result.get("totalCalories"), 0);
            if (calories > 0) {
                result.put("foods", List.of(Map.of(
                        "name", stringValue(result.get("mealName"), "识别餐食"),
                        "weightG", 0,
                        "calories", calories
                )));
            }
        }
    }

    private Map<String, Object> normalizeMealFood(Map<Object, Object> item) {
        Map<String, Object> food = new LinkedHashMap<>();
        food.put("name", stringValue(firstPresent(item, "name", "foodName", "ingredient", "食材", "名称"), "食材"));
        food.put("weightG", numberValue(firstPresent(item, "weightG", "weight", "grams", "gram", "重量", "克数"), 0));
        food.put("calories", numberValue(firstPresent(item, "calories", "kcal", "热量", "卡路里"), 0));
        return food;
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) return map.get(key);
        }
        return null;
    }

    private String stringValue(Object raw, String fallback) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        return value.isBlank() ? fallback : value;
    }

    private double numberValue(Object raw, double fallback) {
        if (raw instanceof Number number) return number.doubleValue();
        String value = raw == null ? "" : String.valueOf(raw);
        Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(value);
        return matcher.find() ? Double.parseDouble(matcher.group()) : fallback;
    }

    private double caloriesFromRaw(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:kcal|千卡|大卡|卡路里)").matcher(raw);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
    }

    private List<String> defaultObservations(String type) {
        return switch (type) {
            case "tongue" -> List.of(
                    "舌色、舌苔和舌体边缘需要结合清晰自然光照片判断；光线偏色或刚进食会让舌苔厚薄、颜色出现误差。",
                    "若舌体看起来偏胖或边缘有齿痕，通常提示水液代谢负担偏重，中医上常从脾虚夹湿角度做生活调理。",
                    "若舌苔偏白且薄，常见于消化功能偏弱或近期饮食寒凉、油腻后，需结合胃口、腹胀和大便情况判断。",
                    "若舌面局部发红、苔少或有裂纹，可能与熬夜、口干、饮水不足或近期上火相关，不能单凭照片定性。",
                    "舌根区域更容易残留舌苔，拍摄时若舌根没有露出，会影响对湿重、厚腻苔的判断。"
            );
            case "hair" -> List.of(
                    "头顶分缝和发际线需要在干发、自然光下观察；油发、阴影和逆光都会放大头皮外露程度。",
                    "若头顶局部能看到头皮，常见原因是发丝直径变细、密度下降或分缝被牵拉变宽，需要连续对比判断。",
                    "若发际线两侧后移更明显，要警惕雄激素相关脱发趋势；核心机制通常是毛囊逐渐微小化。",
                    "若局部呈圆形或片状突然缺发，需要区别斑秃等情况，这类变化不适合只靠居家护理观察。",
                    "若头皮油腻、泛红、屑多并伴随掉发，可能与脂溢性头皮环境有关，炎症和油脂会影响毛囊状态。"
            );
            case "skin" -> List.of(
                    "面部油光、干纹、泛红和毛孔状态需要在素颜、自然光下判断；滤镜和底妆会遮盖真实肤况。",
                    "T 区油光更明显时，通常提示皮脂分泌较旺；两颊紧绷或起皮则更偏向屏障水分不足。",
                    "鼻翼、下巴或额头若有闭口和痘印，多与油脂堆积、角质代谢和局部炎症恢复有关。",
                    "脸颊泛红或刺痛感明显时，可能提示皮肤屏障耐受度下降，应减少叠加刺激性护肤。",
                    "肤色暗沉和局部色沉常与防晒不足、痘印恢复期或作息压力有关，需要持续护理而非一次判断。"
            );
            default -> List.of();
        };
    }

    private Map<String, List<String>> defaultAdviceSections(String type) {
        return switch (type) {
            case "tongue" -> orderedSections(
                    "健脾祛湿饮食推荐 / 忌口", List.of(
                            "主食优先选择米饭、燕麦、山药、南瓜等温和易消化食物，搭配足量优质蛋白，避免长期只吃生冷沙拉。",
                            "湿重、腹胀或大便黏腻时，少吃甜饮、油炸、夜宵、冰品和重辣火锅，连续 1-2 周观察舌苔变化。"
                    ),
                    "睡眠运动祛湿方案", List.of(
                            "尽量在 23 点前入睡，晚餐七分饱，饭后散步 15-30 分钟，帮助脾胃运化和水液代谢。",
                            "每周安排 3-5 次微出汗运动，如快走、骑车、力量训练，避免大汗后马上吹冷风或喝冰饮。"
                    ),
                    "必须看中医的症状预警清单", List.of(
                            "舌苔持续厚腻、口苦口臭、胃胀反酸、大便长期异常或明显乏力，请到正规中医科面诊辨证。",
                            "舌面破溃、疼痛、出血、异常肿块或颜色突然明显改变，应及时到医院排查。"
                    ));
            case "hair" -> orderedSections(
                    "养发饮食营养方案", List.of(
                            "每天保证鸡蛋、鱼虾、瘦肉、豆制品等蛋白质来源，减脂期不要长期极低热量饮食。",
                            "注意铁、锌、维生素 D、B 族和必需脂肪酸摄入，可通过红肉、贝类、坚果、深色蔬菜和奶类补充。"
                    ),
                    "头皮洗护 + 作息减压养护", List.of(
                            "油性头皮可 1-2 天洗一次，干性头皮按出油情况调整；避免长期发油发蜡堵塞头皮。",
                            "减少紧扎、频繁烫染和暴力梳拉，保证 7 小时左右睡眠，连续记录掉发量和分缝宽度变化。"
                    ),
                    "需要皮肤科就诊的脱发加重判定标准", List.of(
                            "连续 4-8 周每日掉发明显增多、头顶分缝变宽或发际线快速后移，建议到皮肤科做毛囊镜评估。",
                            "出现圆形片状脱发、头皮红肿疼痛、渗出、明显瘙痒或大量头屑，不要自行用药，应尽快就诊。"
                    ));
            case "skin" -> orderedSections(
                    "饮食调理", List.of(
                            "痘痘和油脂明显时，先减少高糖饮料、甜点、油炸和频繁夜宵，保持每餐有蔬菜和蛋白质。",
                            "干燥暗沉时保证饮水和优质脂肪摄入，如鱼类、坚果、牛油果，避免过度节食。"
                    ),
                    "生活作息 / 皮肤护理", List.of(
                            "早晚温和清洁，白天足量防晒；屏障不稳时暂停刷酸、酒精类爽肤水和高频去角质。",
                            "痘痘期避免挤压，枕巾口罩勤换，连续 2-4 周用同一套基础护肤观察变化。"
                    ),
                    "就医预警", List.of(
                            "红肿疼痛痘、脓疱、大片脱屑或反复过敏超过 2 周，建议到皮肤科面诊。",
                            "色斑、痣或皮损出现快速变大、出血、颜色不均，应及时就医排查。"
                    ));
            default -> Map.of();
        };
    }

    private Map<String, List<String>> orderedSections(
            String firstTitle, List<String> firstItems,
            String secondTitle, List<String> secondItems,
            String thirdTitle, List<String> thirdItems
    ) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put(firstTitle, firstItems);
        sections.put(secondTitle, secondItems);
        sections.put(thirdTitle, thirdItems);
        return sections;
    }

    private String disclaimer() {
        return "AI 视觉识别仅作日常健康参考，不能替代中医师 / 皮肤科医生线下专业诊断、开药；身体不适或脱发持续加重请前往正规医院就诊。";
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
