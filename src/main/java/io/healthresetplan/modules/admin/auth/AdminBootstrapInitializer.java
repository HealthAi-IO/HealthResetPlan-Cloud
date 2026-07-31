package io.healthresetplan.modules.admin.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final JdbcTemplate jdbc;
    private final String bootstrapPassword;

    public AdminBootstrapInitializer(
            JdbcTemplate jdbc,
            @Value("${app.admin.bootstrap-password:}") String bootstrapPassword) {
        this.jdbc = jdbc;
        this.bootstrapPassword = bootstrapPassword == null ? "" : bootstrapPassword;
    }

    @Override
    public void run(String... args) {
        Long activeAdmins = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_account
                WHERE role_code = 'super_admin' AND status = 1 AND deleted_at IS NULL
                """, Long.class);
        if (activeAdmins != null && activeAdmins > 0) {
            return;
        }
        if (bootstrapPassword.length() < 12) {
            log.error("No active super administrator. Set ADMIN_BOOTSTRAP_PASSWORD "
                    + "to a unique password of at least 12 characters and restart once.");
            return;
        }
        int updated = jdbc.update("""
                UPDATE admin_account
                SET password_hash = ?, status = 1, totp_secret = '', updated_at = NOW(3)
                WHERE username = 'admin' AND deleted_at IS NULL
                """, BCRYPT.encode(bootstrapPassword));
        if (updated == 1) {
            log.warn("Bootstrap administrator activated. Remove ADMIN_BOOTSTRAP_PASSWORD "
                    + "after signing in and enabling TOTP.");
        }
    }
}
