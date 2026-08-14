package io.healthresetplan.modules.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiContentGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiContentGenerationService.class);
    private static final String SYSTEM_PROMPT = """
            你是健康重启计划的生活健康科普编辑。只能创作饮食、运动、睡眠、体重管理和日常健康习惯内容。
            严禁诊断疾病、判断病情、描述药效、推荐药物、给出处方/剂量/停换药建议或替代专业医疗意见。
            内容应谨慎、易懂、可执行，不制造焦虑，不承诺效果。只输出合法JSON，不要Markdown代码块。
            article结构：
            {"type":"article","title":"","summary":"","coverPrompt":"",
             "article":{"bodyHtml":"<p>...</p>"}}
            card结构：
            {"type":"card","title":"","summary":"","coverPrompt":"",
             "card":{"lead":"","points":["","",""],"tip":""}}
            coverPrompt只描述健康生活场景，不包含文字、商标、药品、医疗器械或人物肖像。
            """;

    private final ContentTaskService taskService;
    private final ContentService contentService;
    private final OneApiService oneApiService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public AiContentGenerationService(
            ContentTaskService taskService,
            ContentService contentService,
            OneApiService oneApiService,
            ObjectMapper objectMapper,
            TaskExecutor taskExecutor) {
        this.taskService = taskService;
        this.contentService = contentService;
        this.oneApiService = oneApiService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 15_000L)
    public void runDueTasks() {
        for (Long taskId : taskService.claimDueTasks()) {
            generateAsync(taskId);
        }
    }

    public void generateAsync(long taskId) {
        taskExecutor.execute(() -> generate(taskId));
    }

    void generate(long taskId) {
        try {
            Map<String, Object> task = taskService.get(taskId);
            String type = String.valueOf(task.get("contentType"));
            if (!"card".equals(type)) {
                throw new BusinessException(40001, "图文资讯仅支持人工创建和发布");
            }
            String topic = String.valueOf(task.get("topic"));
            String provider = text(task.get("preferredProvider"));
            OneApiService.AiCompletion completion = oneApiService.completeJsonWithProvider(
                    "system:content-task:" + taskId,
                    List.of(
                            OneApiService.systemMsg(SYSTEM_PROMPT),
                            OneApiService.userMsg("请生成一篇" + type + "类型资讯。主题范围：" + topic
                                    + "。避免与常见网络套话雷同，标题不超过30个汉字。")),
                    provider.isBlank() ? null : provider,
                    4096L);
            JsonNode root = objectMapper.readTree(extractJson(completion.content()));
            if (!type.equals(root.path("type").asText())) {
                throw new BusinessException(50301, "AI返回的资讯类型不匹配");
            }
            String title = required(root, "title");
            String summary = required(root, "summary");
            String coverPrompt = required(root, "coverPrompt");
            String bodyHtml = "";
            Object content;
            if ("article".equals(type)) {
                JsonNode article = root.path("article");
                bodyHtml = required(article, "bodyHtml");
                content = Map.of();
            } else {
                JsonNode card = root.path("card");
                String lead = required(card, "lead");
                String tip = required(card, "tip");
                List<String> points = objectMapper.convertValue(
                        card.path("points"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                if (points.isEmpty()) throw new BusinessException(50301, "AI返回的卡片要点为空");
                content = Map.of("lead", lead, "points", points, "tip", tip);
            }
            boolean safe = ContentSafetyGuard.isSafe(
                    title + summary + bodyHtml + objectMapper.writeValueAsString(content));
            boolean autoPublish = "auto".equals(task.get("publishMode")) && safe;
            long contentId = contentService.createAiContent(
                    type, title, summary, coverPrompt, bodyHtml, content,
                    completion.provider(), "", autoPublish);
            taskService.markResult(taskId, autoPublish ? "published" : "pending_review",
                    safe ? "" : "内容安全校验未通过，已转为待审核");
            log.info("AI health content generated taskId={} contentId={} published={}",
                    taskId, contentId, autoPublish);
        } catch (Exception ex) {
            taskService.markResult(taskId, "failed", ex.getMessage());
            log.warn("AI health content generation failed taskId={} message={}", taskId, ex.getMessage());
        }
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new BusinessException(50301, "AI返回字段缺失：" + field);
        return value;
    }

    private String extractJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (first < 0 || last <= first) throw new BusinessException(50301, "AI未返回有效JSON");
        return text.substring(first, last + 1);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
