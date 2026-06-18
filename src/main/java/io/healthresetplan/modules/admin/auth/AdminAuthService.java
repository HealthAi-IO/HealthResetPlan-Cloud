package io.healthresetplan.modules.admin.auth;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.common.util.JwtUtils;
import io.healthresetplan.config.JwtProperties;
import io.healthresetplan.modules.admin.auth.dto.AdminLoginRequest;
import io.healthresetplan.modules.admin.auth.dto.AdminTokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAuthService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final JdbcTemplate jdbc;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final TotpVerifier totpVerifier;

    public AdminAuthService(JdbcTemplate jdbc,
                            JwtUtils jwtUtils,
                            JwtProperties jwtProperties,
                            TotpVerifier totpVerifier) {
        this.jdbc = jdbc;
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.totpVerifier = totpVerifier;
    }

    @Transactional
    public AdminTokenResponse login(AdminLoginRequest request, HttpServletRequest httpRequest) {
        Map<String, Object> admin = findByUsername(request.getUsername());
        if (admin.isEmpty() || !BCRYPT.matches(request.getPassword(), text(admin.get("password_hash")))) {
            throw new BusinessException(40101, "账号或密码错误");
        }
        if (number(admin.get("status")) != 1) {
            throw new BusinessException(40301, "管理员账号已被禁用");
        }
        if (!totpVerifier.verify(text(admin.get("totp_secret")), request.getTotpCode())) {
            throw new BusinessException(40103, "动态验证码错误");
        }

        long adminId = number(admin.get("id"));
        jdbc.update("""
                UPDATE admin_account
                SET last_login_at = NOW(3), last_login_ip = ?
                WHERE id = ?
                """, clientIp(httpRequest), adminId);
        writeAudit(adminId, "admin_login", "admin_account:" + adminId, httpRequest);
        return issueTokens(admin, httpRequest);
    }

    @Transactional
    public AdminTokenResponse refresh(String refreshToken, HttpServletRequest request) {
        if (!jwtUtils.isRefreshToken(refreshToken)
                || !"admin".equals(jwtUtils.extractActorType(refreshToken))) {
            throw new BusinessException(40102, "无效的管理员 refresh token");
        }

        long adminId;
        try {
            adminId = Long.parseLong(jwtUtils.extractUserId(refreshToken));
        } catch (NumberFormatException exception) {
            throw new BusinessException(40102, "无效的管理员 refresh token");
        }

        String tokenHash = HashUtils.sha256Hex(refreshToken);
        Long sessionCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_session
                WHERE admin_id = ? AND refresh_token_hash = ? AND expires_at > NOW(3)
                """, Long.class, adminId, tokenHash);
        if (sessionCount == null || sessionCount == 0) {
            throw new BusinessException(40102, "管理员会话已过期，请重新登录");
        }

        Map<String, Object> admin = findById(adminId);
        if (admin.isEmpty() || number(admin.get("status")) != 1) {
            throw new BusinessException(40301, "管理员账号已被禁用");
        }

        jdbc.update("DELETE FROM admin_session WHERE refresh_token_hash = ?", tokenHash);
        return issueTokens(admin, request);
    }

    @Transactional
    public void logout(String refreshToken, HttpServletRequest request) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        String tokenHash = HashUtils.sha256Hex(refreshToken);
        List<Map<String, Object>> sessions = jdbc.queryForList(
                "SELECT admin_id FROM admin_session WHERE refresh_token_hash = ?", tokenHash);
        jdbc.update("DELETE FROM admin_session WHERE refresh_token_hash = ?", tokenHash);
        if (!sessions.isEmpty()) {
            long adminId = number(sessions.get(0).get("admin_id"));
            writeAudit(adminId, "admin_logout", "admin_account:" + adminId, request);
        }
    }

    public Map<String, Object> profile(long adminId) {
        Map<String, Object> admin = findById(adminId);
        if (admin.isEmpty()) throw new BusinessException(40401, "管理员不存在");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("adminId", adminId);
        profile.put("username", text(admin.get("username")));
        profile.put("nickname", text(admin.get("nickname")));
        profile.put("roleCode", text(admin.get("role_code")));
        profile.put("permissions", text(admin.get("permissions")));
        profile.put("totpEnabled", !text(admin.get("totp_secret")).isBlank());
        profile.put("lastLoginAt", admin.get("last_login_at"));
        profile.put("lastLoginIp", text(admin.get("last_login_ip")));
        return profile;
    }

    public Map<String, Object> createTotpSetup(long adminId) {
        Map<String, Object> admin = findById(adminId);
        if (admin.isEmpty()) throw new BusinessException(40401, "管理员不存在");
        if (!text(admin.get("totp_secret")).isBlank()) {
            throw new BusinessException(40901, "双因素认证已经启用");
        }
        String secret = totpVerifier.generateSecret();
        String account = URLEncoder.encode(text(admin.get("username")), StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode("HealthResetPlan Admin", StandardCharsets.UTF_8);
        String uri = "otpauth://totp/" + issuer + ":" + account
                + "?secret=" + secret + "&issuer=" + issuer + "&digits=6&period=30";
        return Map.of("secret", secret, "otpauthUri", uri);
    }

    @Transactional
    public void enableTotp(long adminId, String secret, String code, HttpServletRequest request) {
        if (!totpVerifier.verify(secret, code)) {
            throw new BusinessException(40103, "动态验证码错误");
        }
        int updated = jdbc.update("""
                UPDATE admin_account SET totp_secret = ?
                WHERE id = ? AND status = 1 AND deleted_at IS NULL AND totp_secret = ''
                """, secret.trim().toUpperCase(), adminId);
        if (updated == 0) throw new BusinessException(40901, "双因素认证状态已变化，请刷新后重试");
        writeAudit(adminId, "admin_totp_enabled", "admin_account:" + adminId, request);
    }

    @Transactional
    public void disableTotp(long adminId, String code, HttpServletRequest request) {
        Map<String, Object> admin = findById(adminId);
        String secret = text(admin.get("totp_secret"));
        if (secret.isBlank()) return;
        if (!totpVerifier.verify(secret, code)) {
            throw new BusinessException(40103, "动态验证码错误");
        }
        jdbc.update("UPDATE admin_account SET totp_secret = '' WHERE id = ?", adminId);
        writeAudit(adminId, "admin_totp_disabled", "admin_account:" + adminId, request);
    }

    public List<Map<String, Object>> sessions(long adminId) {
        return jdbc.queryForList("""
                SELECT id, ip, user_agent, created_at, expires_at,
                       CASE WHEN expires_at > NOW(3) THEN 1 ELSE 0 END AS active
                FROM admin_session
                WHERE admin_id = ?
                ORDER BY created_at DESC
                LIMIT 50
                """, adminId);
    }

    @Transactional
    public void revokeSession(long adminId, long sessionId, HttpServletRequest request) {
        int deleted = jdbc.update("DELETE FROM admin_session WHERE id = ? AND admin_id = ?", sessionId, adminId);
        if (deleted == 0) throw new BusinessException(40401, "管理员会话不存在");
        writeAudit(adminId, "admin_session_revoked", "admin_session:" + sessionId, request);
    }

    private AdminTokenResponse issueTokens(Map<String, Object> admin, HttpServletRequest request) {
        long adminId = number(admin.get("id"));
        String roleCode = text(admin.get("role_code"));
        String accessToken = jwtUtils.generateAdminAccessToken(adminId, roleCode);
        String refreshToken = jwtUtils.generateAdminRefreshToken(adminId, roleCode);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(jwtUtils.getRefreshExpiry(refreshToken)), ZoneId.systemDefault());

        jdbc.update("""
                INSERT INTO admin_session (
                  admin_id, refresh_token_hash, expires_at, ip, user_agent
                ) VALUES (?, ?, ?, ?, ?)
                """,
                adminId,
                HashUtils.sha256Hex(refreshToken),
                expiresAt,
                clientIp(request),
                text(request.getHeader("User-Agent"))
        );

        return new AdminTokenResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTtlMinutes() * 60L,
                adminId,
                text(admin.get("username")),
                text(admin.get("nickname")),
                roleCode,
                text(admin.get("permissions"))
        );
    }

    private Map<String, Object> findByUsername(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.*, COALESCE(r.permissions, '') AS permissions
                FROM admin_account a
                LEFT JOIN admin_role r ON r.code = a.role_code
                WHERE a.username = ? AND a.deleted_at IS NULL
                LIMIT 1
                """, username == null ? "" : username.trim());
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> findById(long adminId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.*, COALESCE(r.permissions, '') AS permissions
                FROM admin_account a
                LEFT JOIN admin_role r ON r.code = a.role_code
                WHERE a.id = ? AND a.deleted_at IS NULL
                LIMIT 1
                """, adminId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private void writeAudit(long adminId, String action, String target, HttpServletRequest request) {
        jdbc.update("""
                INSERT INTO audit_log (actor_type, actor_id, action, target, ip, detail)
                VALUES ('admin', ?, ?, ?, ?, '{}')
                """, String.valueOf(adminId), action, target, clientIp(request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return text(request.getRemoteAddr());
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(text(value));
    }
}
