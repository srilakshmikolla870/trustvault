package com.trustvault.audit;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditEventWriter {
    private final JdbcTemplate jdbc;

    public AuditEventWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordAuthentication(UUID actorId, boolean allowed) {
        recordAuthentication("AUTH_LOGIN", actorId, allowed,
                allowed ? "LOGIN_SUCCEEDED" : "LOGIN_FAILED");
    }

    public void recordLogout(UUID actorId) {
        recordAuthentication("AUTH_LOGOUT", actorId, true, "LOGOUT_SUCCEEDED");
    }

    public void recordUserManagement(UUID actorId, UUID targetId, String action, String reason) {
        jdbc.update("""
                INSERT INTO audit_events
                    (id, occurred_at, actor_user_id, action, target_type, target_id,
                     outcome, reason_code, request_id, metadata)
                VALUES (?, CURRENT_TIMESTAMP, ?, ?, 'USER', ?, 'ALLOWED', ?, ?, '{}'::jsonb)
                """,
                UUID.randomUUID(), actorId, action, targetId, reason, UUID.randomUUID().toString());
    }

    public void recordDeniedAdminAccess(UUID actorId, String reason) {
        jdbc.update("""
                INSERT INTO audit_events
                    (id, occurred_at, actor_user_id, action, target_type, target_id,
                     outcome, reason_code, request_id, metadata)
                VALUES (?, CURRENT_TIMESTAMP, ?, 'ADMIN_USER_ACCESS', 'ROUTE', NULL,
                        'DENIED', ?, ?, '{}'::jsonb)
                """,
                UUID.randomUUID(), actorId, reason, UUID.randomUUID().toString());
    }

    private void recordAuthentication(String action, UUID actorId, boolean allowed, String reason) {
        String outcome = allowed ? "ALLOWED" : "DENIED";
        jdbc.update("""
                INSERT INTO audit_events
                    (id, occurred_at, actor_user_id, action, target_type, target_id,
                     outcome, reason_code, request_id, metadata)
                VALUES (?, CURRENT_TIMESTAMP, ?, ?, 'USER', ?, ?, ?, ?, '{}'::jsonb)
                """,
                UUID.randomUUID(), actorId, action, actorId, outcome, reason, UUID.randomUUID().toString());
    }
}
