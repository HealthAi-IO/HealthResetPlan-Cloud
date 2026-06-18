package io.healthresetplan.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.healthresetplan.common.util.JwtUtils;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserAccountMapper accountMapper;
    private final JdbcTemplate jdbc;

    public JwtAuthenticationFilter(JwtUtils jwtUtils,
                                   UserAccountMapper accountMapper,
                                   JdbcTemplate jdbc) {
        this.jwtUtils = jwtUtils;
        this.accountMapper = accountMapper;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);
        if (StringUtils.hasText(token)) {
            try {
                if (jwtUtils.isAccessToken(token)) {
                    authenticate(token);
                }
            } catch (JwtException ignored) {
                // token 无效时不设置认证上下文，由 Spring Security 返回 403
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        if ("admin".equals(jwtUtils.extractActorType(token))) {
            authenticateAdmin(token);
            return;
        }

        String userId = jwtUtils.extractUserId(token);
        UserAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUserId, userId));
        if (account != null && account.getStatus() != null && account.getStatus() == 1) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities(account));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }

    private void authenticateAdmin(String token) {
        long adminId;
        try {
            adminId = Long.parseLong(jwtUtils.extractUserId(token));
        } catch (NumberFormatException ignored) {
            return;
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.role_code, COALESCE(r.permissions, '') AS permissions
                FROM admin_account a
                LEFT JOIN admin_role r ON r.code = a.role_code
                WHERE a.id = ? AND a.status = 1 AND a.deleted_at IS NULL
                """, adminId);
        if (rows.isEmpty()) return;

        String roleCode = String.valueOf(rows.get(0).get("role_code"));
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        authorities.add(new SimpleGrantedAuthority(
                "ROLE_" + roleCode.toUpperCase(Locale.ROOT)));
        String permissions = String.valueOf(rows.get(0).get("permissions"));
        if ("*".equals(permissions)) {
            authorities.add(new SimpleGrantedAuthority("PERM_*"));
        } else {
            for (String permission : permissions.split(",")) {
                if (!permission.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("PERM_" + permission.trim()));
                }
            }
        }
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin:" + adminId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private List<SimpleGrantedAuthority> authorities(UserAccount account) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return authorities;
    }
}
