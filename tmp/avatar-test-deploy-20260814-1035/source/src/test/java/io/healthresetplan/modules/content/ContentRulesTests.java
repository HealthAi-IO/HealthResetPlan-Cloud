package io.healthresetplan.modules.content;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.content.dto.ContentTaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ContentRulesTests {

    @Test
    void weeklyTaskRunsNextMondayAtNine() {
        LocalDateTime next = ContentTaskService.nextRun(
                "weekly", 1, LocalTime.of(9, 0),
                LocalDateTime.of(2026, 7, 31, 12, 0));

        assertEquals(LocalDateTime.of(2026, 8, 3, 9, 0), next);
    }

    @Test
    void similarChineseTitlesAreDetected() {
        assertTrue(ContentService.similarity(
                "每天走路对健康有哪些好处",
                "每天走路对健康有什么好处") >= 0.60);
    }

    @Test
    void unsafeMedicalAdviceIsRejected() {
        assertTrue(!ContentSafetyGuard.isSafe("建议服用某种药物并调整用药"));
    }

    @Test
    void articleKeepsInternalAssetUrl() {
        String url = "/api/v1/content/assets?objectKey=files%2Fcontent%2Ftest.enc";
        String html = ContentService.sanitizeArticleHtml("<img src=\"" + url + "\">");

        assertTrue(html.contains(url));
    }

    @Test
    void aiTaskRejectsArticleContent() {
        ContentTaskService service = new ContentTaskService(mock(JdbcTemplate.class));
        ContentTaskRequest request = new ContentTaskRequest(
                "图文资讯任务", "article", "健康生活", "weekly", 1,
                LocalTime.of(9, 0), "auto", "", false, true);

        assertThrows(BusinessException.class, () -> service.create(request, 1));
    }

    @Test
    void aiCardTaskRejectsManualReviewMode() {
        ContentTaskService service = new ContentTaskService(mock(JdbcTemplate.class));
        ContentTaskRequest request = new ContentTaskRequest(
                "科普卡片任务", "card", "健康生活", "weekly", 1,
                LocalTime.of(9, 0), "review", "", false, true);

        assertThrows(BusinessException.class, () -> service.create(request, 1));
    }
}
