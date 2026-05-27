package io.healthresetplan.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.common.util.JwtUtils;
import io.healthresetplan.config.JwtProperties;
import io.healthresetplan.modules.auth.dto.LoginRequest;
import io.healthresetplan.modules.auth.dto.RefreshRequest;
import io.healthresetplan.modules.auth.dto.RegisterRequest;
import io.healthresetplan.modules.auth.dto.TokenResponse;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.entity.UserCredential;
import io.healthresetplan.modules.user.entity.UserSession;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import io.healthresetplan.modules.user.mapper.UserCredentialMapper;
import io.healthresetplan.modules.user.mapper.UserSessionMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class AuthService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final UserAccountMapper accountMapper;
    private final UserCredentialMapper credentialMapper;
    private final UserSessionMapper sessionMapper;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;

    public AuthService(UserAccountMapper accountMapper,
                       UserCredentialMapper credentialMapper,
                       UserSessionMapper sessionMapper,
                       JwtUtils jwtUtils,
                       JwtProperties jwtProperties) {
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.sessionMapper = sessionMapper;
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
    }

    // ── 注册 ─────────────────────────────────────────────────

    @Transactional
    public TokenResponse register(RegisterRequest req, HttpServletRequest httpReq) {
        String identifierHash = HashUtils.sha256Hex(req.getIdentifier());

        Long count = credentialMapper.selectCount(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getCredType, req.getCredType())
                .eq(UserCredential::getIdentifierHash, identifierHash));
        if (count > 0) {
            throw new BusinessException(40901, "该账号已注册");
        }

        String userId = UUID.randomUUID().toString().replace("-", "");

        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setNickname(req.getNickname() != null ? req.getNickname() : "健康用户");
        account.setStatus(1);
        account.setHasCloudSync(0);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.insert(account);

        UserCredential cred = new UserCredential();
        cred.setUserId(userId);
        cred.setCredType(req.getCredType());
        cred.setIdentifierHash(identifierHash);
        cred.setSecretHash(BCRYPT.encode(req.getPassword()));
        cred.setCreatedAt(LocalDateTime.now());
        cred.setUpdatedAt(LocalDateTime.now());
        credentialMapper.insert(cred);

        return buildTokensAndSession(userId, httpReq);
    }

    // ── 登录 ─────────────────────────────────────────────────

    @Transactional
    public TokenResponse login(LoginRequest req, HttpServletRequest httpReq) {
        String identifierHash = HashUtils.sha256Hex(req.getIdentifier());

        UserCredential cred = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getCredType, req.getCredType())
                .eq(UserCredential::getIdentifierHash, identifierHash));
        if (cred == null || !BCRYPT.matches(req.getPassword(), cred.getSecretHash())) {
            throw new BusinessException(40101, "账号或密码错误");
        }

        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, cred.getUserId()));
        if (account == null || account.getStatus() != 1) {
            throw new BusinessException(40301, "账号已被禁用或注销");
        }

        return buildTokensAndSession(cred.getUserId(), httpReq);
    }

    // ── 刷新 ─────────────────────────────────────────────────

    @Transactional
    public TokenResponse refresh(RefreshRequest req, HttpServletRequest httpReq) {
        String token = req.getRefreshToken();

        // isRefreshToken 内部调用 parse()，会校验 JWT 签名和过期时间
        if (!jwtUtils.isRefreshToken(token)) {
            throw new BusinessException(40102, "无效的 refresh token");
        }

        UserSession session = sessionMapper.selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshToken, token));
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40102, "refresh token 已失效，请重新登录");
        }

        String userId = jwtUtils.extractUserId(token);
        sessionMapper.deleteById(session.getId());
        return buildTokensAndSession(userId, httpReq);
    }

    // ── 注销 ─────────────────────────────────────────────────

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        // access token 无状态、短期自然过期；只需删除 DB session
        sessionMapper.delete(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshToken, refreshToken));
    }

    // ── 内部 ─────────────────────────────────────────────────

    private TokenResponse buildTokensAndSession(String userId, HttpServletRequest httpReq) {
        String accessToken = jwtUtils.generateAccessToken(userId);
        String refreshToken = jwtUtils.generateRefreshToken(userId);

        long expiryMs = jwtUtils.getRefreshExpiry(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expiryMs), ZoneId.systemDefault());

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setDeviceId(httpReq.getHeader("X-Device-Id"));
        session.setRefreshToken(refreshToken);
        session.setIp(resolveClientIp(httpReq));
        session.setUserAgent(httpReq.getHeader("User-Agent"));
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        long accessExpiresIn = jwtProperties.getAccessTtlMinutes() * 60L;
        return new TokenResponse(accessToken, refreshToken, accessExpiresIn, userId);
    }

    private String resolveClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
