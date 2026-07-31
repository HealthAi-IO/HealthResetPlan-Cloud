package io.healthresetplan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.result.R;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, ObjectMapper objectMapper, Environment environment) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/api/v1/auth/password/set",
                                "/api/v1/auth/cancel-account",
                                "/api/v1/auth/cancel-account/send-code"
                        ).authenticated()
                        .requestMatchers(
                                "/api/v1/admin/auth/login",
                                "/api/v1/admin/auth/refresh",
                                "/api/v1/admin/auth/logout"
                        ).permitAll()
                        .requestMatchers(
                                "/api/v1/admin/auth/me",
                                "/api/v1/admin/auth/totp/setup",
                                "/api/v1/admin/auth/totp/enable"
                        ).authenticated()
                        .requestMatchers(
                                "/api/v1/admin/system/admins/**",
                                "/api/v1/admin/system/roles"
                        ).hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/vip/**", "/api/v1/admin/orders/**").denyAll()
                        .requestMatchers("/api/v1/admin/feedback/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/**")
                        .hasAnyAuthority("PERM_user:read", "PERM_*")
                        .requestMatchers("/api/v1/admin/analytics/**")
                        .hasAnyAuthority("PERM_analytics:read", "PERM_analytics:export", "PERM_*")
                        .requestMatchers("/api/v1/admin/exports/**")
                        .hasAnyAuthority("PERM_user:export", "PERM_analytics:export", "PERM_feedback:export", "PERM_*")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/platforms/**")
                        .hasAnyAuthority("PERM_platform:read", "PERM_*")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/releases/**")
                        .hasAnyAuthority("PERM_release:read", "PERM_release:write", "PERM_*")
                        .requestMatchers("/api/v1/admin/releases/**")
                        .hasAnyAuthority("PERM_release:write", "PERM_*")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/content/templates/**")
                        .hasAnyAuthority("PERM_plan:read", "PERM_plan:write", "PERM_*")
                        .requestMatchers("/api/v1/admin/content/templates/**")
                        .hasAnyAuthority("PERM_plan:write", "PERM_*")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/health-content/**")
                        .hasAnyAuthority("PERM_content:read", "PERM_content:write", "PERM_content:publish", "PERM_*")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/admin/health-content/*/publish",
                                "/api/v1/admin/health-content/*/offline"
                        ).hasAnyAuthority("PERM_content:publish", "PERM_*")
                        .requestMatchers("/api/v1/admin/health-content/**")
                        .hasAnyAuthority("PERM_content:write", "PERM_*")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/ai/**")
                        .hasAnyAuthority("PERM_ai:read", "PERM_ai:write", "PERM_*")
                        .requestMatchers("/api/v1/admin/ai/**")
                        .hasAnyAuthority("PERM_ai:write", "PERM_*")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/reminders/**")
                        .hasAnyAuthority("PERM_reminder:read", "PERM_*")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/feedback/**")
                        .hasAnyAuthority("PERM_feedback:read", "PERM_feedback:write", "PERM_*")
                        .requestMatchers("/api/v1/admin/feedback/**")
                        .hasAnyAuthority("PERM_feedback:write", "PERM_*")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/system/summary")
                        .hasAnyAuthority("PERM_audit:read", "PERM_*")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/content/assets",
                                "/api/v1/releases/check",
                                "/api/v1/releases/latest",
                                "/api/v1/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler())
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 未登录访问受保护资源时直接写 JSON，避免 forward 到 /error
     * 触发 "response already committed"。
     */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, ex) -> writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                R.fail(40100, "请先登录"));
    }

    /**
     * 已登录但角色不足时统一返回 JSON。
     */
    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, ex) -> writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                R.fail(40300, "无权限访问"));
    }

    private void writeJson(HttpServletResponse response, int status, R<?> body) throws java.io.IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = new java.util.ArrayList<>(List.of(
                "https://admin.jkcqplan.com",
                "https://app.jkcqplan.com",
                "https://jkcqplan.com",
                "https://www.jkcqplan.com",
                "https://api.jkcqplan.com"));
        if (!java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            origins.addAll(List.of("http://localhost:*", "http://127.0.0.1:*", "http://192.168.*.*:*"));
        }
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
