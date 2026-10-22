package com.trustvault.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminActivityService {
    private static final int MAX_EVENTS = 100;
    private final JdbcTemplate jdbc;

    public AdminActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ActivityEntry> latest(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_EVENTS));
        return jdbc.query("""
                SELECT e.id, e.occurred_at, u.display_name AS actor_name, u.email AS actor_email,
                       e.action, e.target_type, e.target_id, e.outcome, e.reason_code
                FROM audit_events e
                LEFT JOIN users u ON u.id = e.actor_user_id
                ORDER BY e.occurred_at DESC, e.id DESC
                LIMIT ?
                """, (result, row) -> map(result), limit);
    }

    private static ActivityEntry map(ResultSet result) throws SQLException {
        return new ActivityEntry(result.getObject("id", UUID.class),
                result.getTimestamp("occurred_at").toInstant(), result.getString("actor_name"),
                result.getString("actor_email"), result.getString("action"), result.getString("target_type"),
                result.getObject("target_id", UUID.class), result.getString("outcome"), result.getString("reason_code"));
    }

    public record ActivityEntry(UUID id, Instant occurredAt, String actorName, String actorEmail,
            String action, String targetType, UUID targetId, String outcome, String reasonCode) {}
}
