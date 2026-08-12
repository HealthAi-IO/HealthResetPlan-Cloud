package io.healthresetplan.modules.content;

import io.healthresetplan.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentInteractionServiceTests {

    @Test
    void rejectsUnknownReaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        ContentInteractionService service = new ContentInteractionService(jdbc);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.react("user-1", 1L, "unknown"));

        assertEquals(40001, error.getCode());
    }

    @Test
    void onlyOwnerCanDeleteComment() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(8L), eq(1L), eq("user-1"))).thenReturn(0);
        ContentInteractionService service = new ContentInteractionService(jdbc);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.deleteComment("user-1", 1L, 8L));

        assertEquals(40401, error.getCode());
        verify(jdbc).update(anyString(), eq(8L), eq(1L), eq("user-1"));
    }
}
