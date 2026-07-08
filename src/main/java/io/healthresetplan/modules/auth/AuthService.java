package io.healthresetplan.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.common.util.JwtUtils;
import io.healthresetplan.config.JwtProperties;
import io.healthresetplan.modules.auth.dto.LoginRequest;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeRequest;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeResponse;
import io.healthresetplan.modules.auth.dto.PasswordResetRequest;
import io.healthresetplan.modules.auth.dto.RefreshRequest;
import io.healthresetplan.modules.auth.dto.RegisterRequest;
import io.healthresetplan.modules.auth.dto.SmsLoginCodeRequest;
import io.healthresetplan.modules.auth.dto.SmsLoginRequest;
import io.healthresetplan.modules.auth.dto.TokenResponse;
import io.healthresetplan.modules.sms.SmsVerificationService;
import io.healthresetplan.modules.sync.KeyRetentionService;
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

        UserCredential credential = new UserCredential();
        credential.setUserId(userId);
        credential.setCredType(credType);
        credential.setIdentifierHash(identifierHash);
        credential.setSecretHash(BCRYPT.encode(req.getPassword()));
        credential.setCreatedAt(LocalDateTime.now());
        credential.setUpdatedAt(LocalDateTime.now());
        credentialMapper.insert(credential);

        return buildTokensAndSession(userId, httpReq);
    }

    @Transactional
    public TokenResponse login(LoginRequest req, HttpServletRequest httpReq) {
        UserCredential credential = findCredential(req.getCredType(), req.getIdentifier());
        if (credential == null || !BCRYPT.matches(req.getPassword(), credential.getSecretHash())) {
            throw new BusinessException(40101, "账号或密码错误");
        }

        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, credential.getUserId()));
        if (account == null || account.getStatus() != 1) {
            throw new BusinessException(40301, "账号已被禁用或注销");
        }

        backfillPhoneTail(account, req.getCredType(), req.getIdentifier());
        return buildTokensAndSession(credential.getUserId(), httpReq);
    }

    public PasswordResetCodeResponse sendSmsLoginCode(SmsLoginCodeRequest req) {
        String phone = normalizePhone(req.getPhone());
        validatePhone(phone);
        SmsVerificationService.SendCodeResult result = smsVerificationService.sendPhoneCode(
                SmsVerificationService.SCENE_AUTH,
                phone
        );
        return new PasswordResetCodeResponse(result.debugCode(), result.expiresIn());
    }

    @Transactional
    public TokenResponse smsLogin(SmsLoginRequest req, HttpServletRequest httpReq) {
        String phone = normalizePhone(req.getPhone());
        validatePhone(phone);
        smsVerificationService.verifyPhoneCode(SmsVerificationService.SCENE_AUTH, phone, req.getCode());

        String identifierHash = HashUtils.sha256Hex(phone);
        UserCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getCredType, "phone")
                .eq(UserCredential::getIdentifierHash, identifierHash));

        String userId;
        if (credential == null) {
            userId = createPhoneAccount(phone, req.getNickname());
        } else {
            UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                    .eq(UserAccount::getUserId, credential.getUserId()));
            if (account == null || account.getStatus() != 1) {
                throw new BusinessException(40301, "account disabled");
            }
            backfillPhoneTail(account, "phone", phone);
            userId = credential.getUserId();
        }

        return buildTokensAndSession(userId, httpReq);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req, HttpServletRequest httpReq) {
        String refreshToken = req.getRefreshToken();
        if (!jwtUtils.isRefreshToken(refreshToken)) {
            throw new BusinessException(40102, "无效的 refresh token");
        }

        UserSession session = sessionMapper.selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshToken, refreshToken));
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40102, "refresh token 已失效，请重新登录");
        }

        String userId = jwtUtils.extractUserId(refreshToken);
        sessionMapper.deleteById(session.getId());
        return buildTokensAndSession(userId, httpReq);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
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

        LocalDateTime now = LocalDateTime.now();
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
        UserCredential credential = findCredential(credType, identifier);
        if (credential == null) {
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

        UserCredential credential = findCredential(credType, identifier);
        if (credential == null) {
            throw new BusinessException(40401, "账号不存在");
        }
        credential.setSecretHash(BCRYPT.encode(req.getNewPassword()));
        credential.setUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(credential);

        sessionMapper.delete(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, credential.getUserId()));
    }

    private TokenResponse buildTokensAndSession(String userId, HttpServletRequest httpReq) {
        String accessToken = jwtUtils.generateAccessToken(userId);
        String refreshToken = jwtUtils.generateRefreshToken(userId);

        long expiryMs = jwtUtils.getRefreshExpiry(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expiryMs), ZoneId.systemDefault());

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setDeviceId(nullToEmpty(httpReq.getHeader("X-Device-Id")));
        session.setPlatform(normalizePlatform(httpReq.getHeader("X-Platform"), httpReq.getHeader("User-Agent")));
        session.setAppVersion(nullToEmpty(httpReq.getHeader("X-App-Version")));
        session.setChannel(defaultChannel(httpReq.getHeader("X-Channel")));
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
        UserCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getCredType, normalizedCredType)
                .eq(UserCredential::getIdentifierHash, HashUtils.sha256Hex(normalizedIdentifier)));
        if (credential != null || normalizedIdentifier.equals(identifier)) {
            return credential;
        }
        return credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getCredType, normalizedCredType)
                .eq(UserCredential::getIdentifierHash, HashUtils.sha256Hex(identifier)));
    }

    private String createPhoneAccount(String phone, String nickname) {
        String userId = generateUserId();
        LocalDateTime now = LocalDateTime.now();

        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setCustomId("");
        account.setPhoneTail(phoneTail("phone", phone));
        account.setNickname(nickname != null && !nickname.isBlank() ? nickname.trim() : "健康用户");
        account.setStatus(1);
        account.setRoleCode("user");
        account.setHasCloudSync(0);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountMapper.insert(account);

        UserCredential credential = new UserCredential();
        credential.setUserId(userId);
        credential.setCredType("phone");
        credential.setIdentifierHash(HashUtils.sha256Hex(phone));
        credential.setSecretHash("");
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credentialMapper.insert(credential);

        return userId;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeCredType(String credType) {
        return credType == null ? "" : credType.trim().toLowerCase();
    }

    private static String normalizeIdentifier(String credType, String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if ("phone".equals(credType)) {
            return normalizePhone(value);
        }
        return "email".equals(credType) ? value.toLowerCase() : value;
    }

    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private static void validatePhone(String phone) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new BusinessException(40003, "phone format is invalid");
        }
    }

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
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 10; i++) {
            long num = 100_000_000_000L + (long) (random.nextDouble() * 899_999_999_999L);
            String id = String.valueOf(num);
            Long exists = accountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                    .eq(UserAccount::getUserId, id));
            if (exists == null || exists == 0) {
                return id;
            }
        }
        return String.valueOf(100_000_000_000L + System.nanoTime() % 899_999_999_999L);
    }

    private String resolveClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private String defaultChannel(String channel) {
        String value = nullToEmpty(channel).trim();
        return value.isEmpty() ? "official" : value;
    }

    private String normalizePlatform(String platform, String userAgent) {
        String value = nullToEmpty(platform).trim().toLowerCase();
        if (!value.isEmpty()) {
            return value;
        }
        String ua = nullToEmpty(userAgent).toLowerCase();
        if (ua.contains("windows")) return "windows";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "macos";
        if (ua.contains("android")) return "android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "ios";
        if (ua.contains("micromessenger")) return "wechat";
        return "web";
    }
}
