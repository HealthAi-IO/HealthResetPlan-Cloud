package io.healthresetplan.common.util;

import io.healthresetplan.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与校验工具。
 *
 * <p>Access token  (type=access)  — 短期，60 分钟</p>
 * <p>Refresh token (type=refresh) — 长期，30 天，同时入库 user_session</p>
 */
@Component
public class JwtUtils {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtils(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    // ── 生成 ─────────────────────────────────────────────────

    public String generateAccessToken(String userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getAccessTtlMinutes() * 60_000L))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getRefreshTtlDays() * 86_400_000L))
                .signWith(key)
                .compact();
    }

    // ── 校验 ─────────────────────────────────────────────────

    /**
     * 解析并校验 token。返回 Claims；失败抛 {@link JwtException}。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从合法 token 中提取 userId（subject）。 */
    public String extractUserId(String token) {
        return parse(token).getSubject();
    }

    /** 判断是否为 access token。 */
    public boolean isAccessToken(String token) {
        try {
            return TYPE_ACCESS.equals(parse(token).get(CLAIM_TYPE, String.class));
        } catch (JwtException e) {
            return false;
        }
    }

    /** 判断是否为 refresh token。 */
    public boolean isRefreshToken(String token) {
        try {
            return TYPE_REFRESH.equals(parse(token).get(CLAIM_TYPE, String.class));
        } catch (JwtException e) {
            return false;
        }
    }

    /** 返回 refresh token 到期时间（毫秒时间戳）。 */
    public long getRefreshExpiry(String token) {
        return parse(token).getExpiration().getTime();
    }
}
