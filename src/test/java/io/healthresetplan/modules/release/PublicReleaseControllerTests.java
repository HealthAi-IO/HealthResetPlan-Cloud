package io.healthresetplan.modules.release;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicReleaseControllerTests {

    @Test
    void latestReturnsWebsiteDownloadFields() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 24, 10, 46);
        when(jdbc.queryForList(anyString(), anyString(), anyString())).thenReturn(List.of(Map.of(
                "version_name", "1.0.2",
                "package_size_mb", new BigDecimal("14.45"),
                "package_url", "https://jkcqplan.com/downloads/windows/app.zip",
                "updated_at", updatedAt
        )));

        Map<String, Object> data = new PublicReleaseController(jdbc)
                .latest("Windows", "official")
                .getData();

        assertThat(data).containsEntry("available", true);
        assertThat(data).containsEntry("platform", "windows");
        assertThat(data).containsEntry("version", "1.0.2");
        assertThat(data).containsEntry("sizeMb", new BigDecimal("14.45"));
        assertThat(data).containsEntry("downloadUrl", "https://jkcqplan.com/downloads/windows/app.zip");
        assertThat(data).containsEntry("updatedAt", updatedAt);
    }
}
