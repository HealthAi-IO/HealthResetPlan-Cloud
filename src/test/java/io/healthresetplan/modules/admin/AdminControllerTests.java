package io.healthresetplan.modules.admin;

import io.healthresetplan.modules.ai.oneapi.OneApiService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

class AdminControllerTests {

    @Test
    void usersDoesNotQueryRemovedSyncRecordTable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(2L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        new AdminController(jdbc, mock(OneApiService.class)).users(null, 1, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).doesNotContain("sync_record");
    }

    @Test
    void platformSummaryGroupsByNormalizedPlatform() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());

        new AdminController(jdbc, mock(OneApiService.class)).platformSummary();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForList(sql.capture());
        assertThat(sql.getAllValues()).anySatisfy(query -> {
            assertThat(query).contains(") normalized", "GROUP BY normalized.platform");
            assertThat(query).doesNotContain("GROUP BY platform\n");
        });
    }
}
