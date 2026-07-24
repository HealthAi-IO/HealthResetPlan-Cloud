package io.healthresetplan.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.common.util.JwtUtils;
import io.healthresetplan.config.JwtProperties;
import io.healthresetplan.modules.auth.dto.LoginRequest;
import io.healthresetplan.modules.auth.dto.PhonePasswordLoginRequest;
import io.healthresetplan.modules.auth.dto.PhoneRegisterRequest;
import io.healthresetplan.modules.auth.dto.PhoneRegisterVerifyRequest;
import io.healthresetplan.modules.auth.dto.PhoneVerificationResponse;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeRequest;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeResponse;
import io.healthresetplan.modules.auth.dto.PasswordResetRequest;
import io.healthresetplan.modules.auth.dto.AccountRecoveryRequest;
import io.healthresetplan.modules.auth.dto.CancelAccountRequest;
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
import io.healthresetplan.modules.user.mapper.UserKeyMetaMapper;
import io.healthresetplan.modules.user.mapper.UserSessionMapper;
import io.healthresetplan.modules.user.entity.UserKeyMeta;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AuthService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final UserAccountMapper accountMapper;
    private final UserCredentialMapper credentialMapper;
    private final UserSessionMapper sessionMapper;
    private final UserKeyMetaMapper keyMetaMapper;
    private final KeyRetentionService keyRetentionService;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final SmsVerificationService smsVerificationService;
    private final PhoneRegistrationTicketService registrationTicketService;
    private final PasswordLoginThrottleService passwordLoginThrottleService;
    private final JdbcTemplate jdbc;

    public AuthService(UserAccountMapper accountMapper,
                       UserCredentialMapper credentialMapper,
                       UserSessionMapper sessionMapper,
                       UserKeyMetaMapper keyMetaMapper,
                       KeyRetentionService keyRetentionService,
                       JwtUtils jwtUtils,
                       JwtProperties jwtProperties,
                       SmsVerificationService smsVerificationService,
                       PhoneRegistrationTicketService registrationTicketService,
                       PasswordLoginThrottleService passwordLoginThrottleService,
                       JdbcTemplate jdbc) {
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.sessionMapper = sessionMapper;
        this.keyMetaMapper = keyMetaMapper;
        this.keyRetentionService = keyRetentionService;
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.smsVerificationService = smsVerificationService;
        this.registrationTicketService = registrationTicketService;
        this.passwordLoginThrottleService = passwordLoginThrottleService;
        this.jdbc = jdbc;
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
        account.setCustomId(userId);
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

    public PhoneVerificationResponse verifyPhone(PhoneRegisterVerifyRequest req, HttpServletRequest httpReq) {
        String phone = normalizePhone(req.getPhone());
        validatePhone(phone);
        smsVerificationService.verifyPhoneCode(SmsVerificationService.SCENE_AUTH, phone, req.getCode());
        UserCredential credential = findCredential("phone", phone);
        if (credential == null) return PhoneVerificationResponse.register(registrationTicketService.issue(phone));
        return PhoneVerificationResponse.login(buildTokensAndSession(credential.getUserId(), httpReq));
    }

    @Transactional
    public TokenResponse registerPhone(PhoneRegisterRequest req, HttpServletRequest httpReq) {
        String phone = normalizePhone(req.getPhone());
        validatePhone(phone);
        String password = req.getPassword();
        if (password != null && !password.isBlank()) {
            validatePassword(password);
        }
        if (!req.isAgreedToTerms()) throw new BusinessException(40003, "请先同意用户协议和隐私政策");
        registrationTicketService.verify(req.getRegistrationTicket(), phone);
        if (findCredential("phone", phone) != null) {
            throw new BusinessException(40901, "该手机号已绑定账号，请直接登录");
        }

        String userId = generateUserId();
        LocalDateTime now = LocalDateTime.now();
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setCustomId(userId);
        account.setPhoneTail(phoneTail("phone", phone));
        account.setNickname(req.getNickname() != null && !req.getNickname().isBlank()
                ? req.getNickname().trim() : "健康用户");
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
        credential.setSecretHash(password == null || password.isBlank() ? "" : BCRYPT.encode(password));
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credentialMapper.insert(credential);

        jdbc.update("INSERT INTO user_registration_consent (user_id, agreement_version, accepted_at) VALUES (?, ?, ?)",
                userId, req.getAgreementVersion(), now);

        registrationTicketService.consume(req.getRegistrationTicket());
        return buildTokensAndSession(userId, httpReq);
    }

    @Transactional
    public TokenResponse loginWithPhonePassword(PhonePasswordLoginRequest req, HttpServletRequest httpReq) {
        String phone = normalizePhone(req.getPhone());
        validatePhone(phone);
        String ip = nullToEmpty(resolveClientIp(httpReq));
        passwordLoginThrottleService.check(phone, ip);
        UserCredential credential = findCredential("phone", phone);
        if (credential == null || credential.getSecretHash() == null || credential.getSecretHash().isBlank()
                || !BCRYPT.matches(req.getPassword(), credential.getSecretHash())) {
            passwordLoginThrottleService.recordFailure(phone, ip);
            throw new BusinessException(40101, "手机号或密码错误");
        }
        passwordLoginThrottleService.clear(phone, ip);
        return buildTokensAndSession(credential.getUserId(), httpReq);
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

        if (credential == null) {
            throw new BusinessException(40401, "账号不存在，请先注册");
        }

        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, credential.getUserId()));
        if (account == null || account.getStatus() != 1) {
            throw new BusinessException(40301, "账号已被禁用或注销");
        }
        backfillPhoneTail(account, "phone", phone);

        return buildTokensAndSession(credential.getUserId(), httpReq);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req, HttpServletRequest httpReq) {
        String refreshToken = req.getRefreshToken();
        if (!jwtUtils.isRefreshToken(refreshToken)) {
            throw new BusinessException(40102, "无效的 refresh token");
        }

        UserSession session = sessionMapper.selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshToken, HashUtils.sha256Hex(refreshToken)));
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
                .eq(UserSession::getRefreshToken, HashUtils.sha256Hex(refreshToken)));
    }

    public PasswordResetCodeResponse sendCancelAccountCode(String userId, String phone) {
        String normalizedPhone = normalizeIdentifier("phone", phone);
        requireOwnedPhone(userId, normalizedPhone);
        SmsVerificationService.SendCodeResult result = smsVerificationService.sendPhoneCode(
                SmsVerificationService.SCENE_ACCOUNT_CANCEL, normalizedPhone);
        return new PasswordResetCodeResponse(result.debugCode(), result.expiresIn());
    }

    @Transactional
    public void cancelAccount(String userId, CancelAccountRequest req) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(40101, "请先登录账号");
        }
        String phone = normalizeIdentifier("phone", req.getPhone());
        requireOwnedPhone(userId, phone);
        smsVerificationService.verifyPhoneCode(
                SmsVerificationService.SCENE_ACCOUNT_CANCEL, phone, req.getCode());

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

    @Transactional
    public TokenResponse reactivateAccount(AccountRecoveryRequest req, HttpServletRequest httpReq) {
        String phone = normalizeIdentifier("phone", req.getPhone());
        smsVerificationService.verifyPhoneCode(
                SmsVerificationService.SCENE_ACCOUNT_RECOVERY, phone, req.getCode());

        UserCredential credential = findCredential("phone", phone);
        if (credential == null) {
            throw new BusinessException(40401, "账号不存在");
        }
        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, credential.getUserId()));
        if (account == null || account.getStatus() == null || account.getStatus() != -1) {
            throw new BusinessException(40003, "该账号不在可恢复状态");
        }

        UserKeyMeta key = keyMetaMapper.selectOne(new LambdaQueryWrapper<UserKeyMeta>()
                .eq(UserKeyMeta::getUserId, account.getUserId())
                .eq(UserKeyMeta::getPublicFinger, req.getKeyFingerprint().toLowerCase())
                .eq(UserKeyMeta::getPurgeStatus, "retaining")
                .gt(UserKeyMeta::getRetentionUntil, LocalDateTime.now())
                .last("LIMIT 1"));
        if (key == null) {
            throw new BusinessException(40301, "助记词不匹配或数据保留期已结束");
        }

        LocalDateTime now = LocalDateTime.now();
        accountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getUserId, account.getUserId())
                .set(UserAccount::getStatus, 1)
                .set(UserAccount::getHasCloudSync, 1)
                .set(UserAccount::getUpdatedAt, now));
        keyRetentionService.markUsed(account.getUserId(), req.getKeyFingerprint().toLowerCase());
        return buildTokensAndSession(account.getUserId(), httpReq);
    }

    public PasswordResetCodeResponse sendPasswordResetCode(PasswordResetCodeRequest req) {
        String credType = normalizeCredType(req.getCredType());
        String identifier = normalizeIdentifier(credType, req.getIdentifier());
        UserCredential credential = findCredential(credType, identifier);
        if (credential == null) {
            return new PasswordResetCodeResponse("", 600);
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

    public PasswordResetCodeResponse sendAccountRecoveryCode(String phone) {
        String normalizedPhone = normalizeIdentifier("phone", phone);
        UserCredential credential = findCredential("phone", normalizedPhone);
        if (credential == null) {
            return new PasswordResetCodeResponse("", 600);
        }
        SmsVerificationService.SendCodeResult result = smsVerificationService.sendPhoneCode(
                SmsVerificationService.SCENE_ACCOUNT_RECOVERY, normalizedPhone);
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

    @Transactional
    public void setInitialPassword(String userId, String password) {
        if (userId == null || userId.isBlank() || "anonymousUser".equals(userId)) {
            throw new BusinessException(40101, "请先登录账号");
        }
        validatePassword(password);
        UserCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getUserId, userId)
                .eq(UserCredential::getCredType, "phone")
                .last("LIMIT 1"));
        if (credential == null) {
            throw new BusinessException(40401, "账号不存在");
        }
        if (credential.getSecretHash() != null && !credential.getSecretHash().isBlank()) {
            throw new BusinessException(40901, "密码已设置，请使用忘记密码功能修改");
        }
        credential.setSecretHash(BCRYPT.encode(password));
        credential.setUpdatedAt(LocalDateTime.now());
        credentialMapper.updateById(credential);
    }

    private TokenResponse buildTokensAndSession(String userId, HttpServletRequest httpReq) {
        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
            throw new BusinessException(40301, "账号已被禁用或注销");
        }

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
        session.setRefreshToken(HashUtils.sha256Hex(refreshToken));
        session.setIp(nullToEmpty(resolveClientIp(httpReq)));
        session.setUserAgent(nullToEmpty(httpReq.getHeader("User-Agent")));
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        long accessExpiresIn = jwtProperties.getAccessTtlMinutes() * 60L;
        return new TokenResponse(accessToken, refreshToken, accessExpiresIn, userId, hasPassword(userId));
    }

    private boolean hasPassword(String userId) {
        UserCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getUserId, userId)
                .eq(UserCredential::getCredType, "phone")
                .last("LIMIT 1"));
        return credential != null && credential.getSecretHash() != null && !credential.getSecretHash().isBlank();
    }

    private void requireOwnedPhone(String userId, String phone) {
        UserCredential credential = findCredential("phone", phone);
        if (credential == null || !credential.getUserId().equals(userId)) {
            throw new BusinessException(40003, "手机号与当前账号不匹配");
        }
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
        account.setCustomId(userId);
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

    private static String normalizeAccountName(String accountName) {
        String value = accountName == null ? "" : accountName.trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.matches("^[\\p{IsHan}A-Za-z0-9_]{3,20}$")) {
            throw new BusinessException(40003, "账户名称仅支持 3-20 位中文、字母、数字或下划线");
        }
        return value;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new BusinessException(40003, "密码长度需为 8-64 位");
        }
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
