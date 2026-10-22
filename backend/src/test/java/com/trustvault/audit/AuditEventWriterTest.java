package com.trustvault.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuditEventWriterTest {
    @Mock private JdbcTemplate jdbc;

    @Test
    void deniedSecurityEventsUseSafeAppendOnlyInsertShape() {
        AuditEventWriter writer = new AuditEventWriter(jdbc);
        UUID userId = UUID.randomUUID();

        writer.recordInactiveAccountRequest(userId);
        writer.recordDeniedCsrfRequest(userId);

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.times(2)).update(statements.capture(), parameters.capture());

        for (String sql : statements.getAllValues()) {
            assertTrue(sql.contains("INSERT INTO audit_events"));
            assertFalse(sql.toLowerCase().contains("password"));
            assertFalse(sql.toLowerCase().contains("storage_key"));
            assertFalse(sql.toLowerCase().contains("content"));
        }
        assertTrue(statements.getAllValues().get(0).contains("'AUTH_SESSION'"));
        assertTrue(statements.getAllValues().get(1).contains("'CSRF_REJECTED'"));
        for (Object[] values : parameters.getAllValues()) {
            for (Object value : values) {
                assertFalse(value instanceof byte[], "audit parameters must not include file bytes");
                assertFalse(value instanceof String text && text.contains("password-hash"));
            }
        }
    }
}
