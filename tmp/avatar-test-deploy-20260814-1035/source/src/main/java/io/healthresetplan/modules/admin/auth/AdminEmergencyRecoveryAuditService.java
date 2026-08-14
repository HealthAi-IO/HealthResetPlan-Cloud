package io.healthresetplan.modules.admin.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Internal-only audit writer for the offline TOTP emergency recovery runbook. */
@Service
public class AdminEmergencyRecoveryAuditService {

    private final JdbcTemplate jdbc;

    public AdminEmergencyRecoveryAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(long operatorAdminId, long recoveredAdminId, String reason, String ip) {
        jdbc.update("""
                INSERT INTO audit_log (actor_type, actor_id, action, target, ip, detail)
                VALUES ('admin', ?, 'admin_totp_emergency_recovery', ?, ?, ?)
                """, String.valueOf(operatorAdminId), "admin_account:" + recoveredAdminId,
                ip == null ? "" : ip, reason == null ? "" : reason.trim());
    }
}
