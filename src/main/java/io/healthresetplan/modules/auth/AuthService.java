package io.healthresetplan.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.common.util.JwtUtils;
import io.healthresetplan.config.JwtProperties;
import io.healthresetplan.modules.sync.KeyRetentionService;
import io.healthresetplan.modules.auth.dto.LoginRequest;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeRequest;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeResponse;
import io.healthresetplan.modules.auth.dto.PasswordResetRequest;
import io.healthresetplan.modules.auth.dto.RefreshRequest;
import io.healthresetplan.modules.auth.dto.RegisterRequest;
import io.healthresetplan.modules.auth.dto.TokenResponse;
import io.healthresetplan.modules.sms.SmsVerificationService;
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

@Service
public class AuthService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final UserAccountMapper accountMapper;
    private final UserCredentialMapper credentialMapper;
    private final UserSessionMapper sessionMapper;
    private final KeyRetentionService keyRetentionService;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final SmsVerificationService smsVerificationService;

    public AuthService(UserAccountMapper accountMapper,
                       UserCredentialMapper credentialMapper,
                       UserSessionMapper sessionMapper,
                       KeyRetentionService keyRetentionService,
                       JwtUtils jwtUtils,
                       JwtProperties jwtProperties,
                       SmsVerificationService smsVerificationService) {
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.sessionMapper = sessionMapper;
        this.keyRetentionService = keyRetentionService;
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.smsVerificationService = smsVerificationService;
    }

    // ── 注册 ─────────────────────────────────────────────────

    @Transactional
    public TokenResponse register(RegisterRequest req, HttpServletRequest httpReq) {
        String credType = normalizeCredType(req.getCredType());
        String identifier = normalizeIdentifier(credType, req.getIdentifier());
        String identifierHash = HashUtils.sha256Hex(identifier);

        Long count = credentialMapper.selectCount(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getCredType, credType)
                .eq(UserCredential::getIdentifierHash, identifierHash));
        if (count == 0 && !identifier.equals(req.getIdentifier())) {
            count = credentialMapper.selectCount(new LambdaQueryWrapper<UserCredential>()
                    .eq(UserCredential::getCredType, credType)
                    .eq(UserCredential::getIdentifierHash, HashUtils.sha256Hex(req.getIdentifier())));
        }
        if (count > 0) {
            throw new BusinessException(40901, "该账号已注册");
        }

        String userId = generateUserId();

        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setCustomId("");
        account.setPhoneTail(phoneTail(credType, identifier));
        account.setNickname(req.getNickname() != null ? req.getNickname() : "健康用户");
        account.setStatus(1);
        account.setRoleCode("user");
        account.setHasCloudSync(0);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.insert(account);

        UserCredential cred = new UserCredential();
        cred.setUserId(userId);
        cred.setCredType(credType);
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
        UserCredential cred = findCredential(req.getCredType(), req.getIdentifier());
        if (cred == null || !BCRYPT.matches(req.getPassword(), cred.getSecretHash())) {
            throw new BusinessException(40101, "账号或密码错误");
        }

        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, cred.getUserId()));
        if (account == null || account.getStatus() != 1) {
            throw new BusinessException(40301, "账号已被禁用或注销");
        }

        backfillPhoneTail(account, req.getCredType(), req.getIdentifier());
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

    @Transactional
    public void cancelAccount(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(40101, "请先登录账号");
        }

        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        if (account == null) {
            throw new BusinessException(40401, "账号不存在");
        }
        if (account.getStatus() != null && account.getStatus() == -1) {
            keyRetentionService.startRetentionForAccount(userId);
            return;
        }

        var now = LocalDateTime.now();
        accountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId)
                .set(UserAccount::getStatus, -1)
                .set(UserAccount::getHasCloudSync, 0)
                .set(UserAccount::getUpdatedAt, now));

        sessionMapper.delete(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId));

        keyRetentionService.startRetentionForAccount(userId);
    }

    public PasswordResetCodeResponse sendPasswordResetCode(PasswordResetCodeRequest req) {
        String credType = normalizeCredType(req.getCredType());
        String identifier = normalizeIdentifier(credType, req.getIdentifier());
        UserCredential cred = findCredential(credType, identifier);
        if (cred == null) {
            throw new BusinessException(40401, "账号不存在");
        }
        if (!"phone".equals(credType)) {
            throw new BusinessException(40003, "暂不支持邮箱验证码，请使用手机号找回密码");
        }

        SmsVerificationService.SendCodeResult result = smsVerificationService.sendPhoneCode(
                SmsVerificationService.SCENE_PASSWORD_RESET,
                identifier
        );
        return new PasswordResetCodeResponse(result.debugCode(), result.expiresIn());
    }

    @Transactional
    public void resetPassword(PasswordResetRequest req) {
        String credType = normalizeCredType(req.getCredType());
        String identifier = normalizeIdentifier(credType, req.getIdentifier());
        if (!"phone".equals(credType)) {
            throw new BusinessException(40003, "暂不支持邮箱验证码，请使用手机号找回密码");
        }

        smsVerificationService.verifyPhoneCode(
                SmsVerificationService.SCENE_PASSWORD_RESET,
                identifier,
                req.getCode()
        );

        UserCredential cred = findCredential(credType, identifier);
        if (cred == null) {
            throw new BusinessException(40401, "账号不存在");
        }
        cred.setSecretHash(BCRYPT.encode(req.getNewPassword()));
        cred.setUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(cred);

        sessionMapper.delete(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, cred.getUserId()));
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
        // 数据库中这些字段都是 NOT NULL，请求未提供时用空字符串兜底
        session.setDeviceId(nullToEmpty(httpReq.getHeader("X-Device-Id")));
        session.setRefreshToken(refreshToken);
        session.setIp(nullToEmpty(resolveClientIp(httpReq)));
        session.setUserAgent(nullToEmpty(httpReq.getHeader("User-Agent")));
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        long accessExpiresIn = jwtProperties.getAccessTtlMinutes() * 60L;
        return new TokenResponse(accessToken, refreshToken, accessExpiresIn, userId);
    }

    private UserCredential findCredential(String credType, String identifier) {
        String normalizedCredType = normalizeCredType(credType);
        String normalizedIdentifier = normalizeIdentifier(normalizedCredType, identifier);
        UserCredential cred = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getCredType, normalizedCredType)
                .eq(UserCredential::getIdentifierHash, HashUtils.sha256Hex(normalizedIdentifier)));
        if (cred != null || normalizedIdentifier.equals(identifier)) {
            return cred;
        }
        return credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getCredType, normalizedCredType)
                .eq(UserCredential::getIdentifierHash, HashUtils.sha256Hex(identifier)));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeCredType(String credType) {
        return credType == null ? "" : credType.trim().toLowerCase();
    }

    private static String normalizeIdentifier(String credType, String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        return "email".equals(credType) ? value.toLowerCase() : value;
    }

    /** 生成 12 位不重复纯数字用户 ID */
    private void backfillPhoneTail(UserAccount account, String credType, String identifier) {
        if (account.getPhoneTail() != null && !account.getPhoneTail().isBlank()) {
            return;
        }
        String tail = phoneTail(normalizeCredType(credType), normalizeIdentifier(credType, identifier));
        if (tail.isBlank()) {
            return;
        }
        account.setPhoneTail(tail);
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);
    }

    private static String phoneTail(String credType, String identifier) {
        if (!"phone".equals(credType) || identifier == null) {
            return "";
        }
        String digits = identifier.replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : "";
    }

    private String generateUserId() {
        var random = new java.security.SecureRandom();
        for (int i = 0; i < 10; i++) {
            long num = 100_000_000_000L + (long)(random.nextDouble() * 899_999_999_999L);
            String id = String.valueOf(num);
            Long exists = accountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                    .eq(UserAccount::getUserId, id));
            if (exists == null || exists == 0) {
                return id;
            }
        }
        // 极端兜底（理论上不会到这里）
        return String.valueOf(100_000_000_000L + System.nanoTime() % 899_999_999_999L);
    }

    private String resolveClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
