package io.healthresetplan.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.persistence.ExpiringStateStore;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.modules.auth.dto.SocialPhoneVerifyRequest;
import io.healthresetplan.modules.auth.dto.TokenResponse;
import io.healthresetplan.modules.sms.SmsVerificationService;
import io.healthresetplan.modules.files.FileStorageService;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.entity.UserCredential;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import io.healthresetplan.modules.user.mapper.UserCredentialMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SocialAuthService {
    private static final Duration TICKET_TTL = Duration.ofMinutes(10);
    private final SocialAuthProperties properties;
    private final ExpiringStateStore stateStore;
    private final ObjectMapper objectMapper;
    private final SmsVerificationService sms;
    private final UserAccountMapper accounts;
    private final UserCredentialMapper credentials;
    private final AuthService authService;
    private final FileStorageService fileStorageService;
    private final HttpClient http = HttpClient.newHttpClient();

    public SocialAuthService(SocialAuthProperties properties, ExpiringStateStore stateStore,
                             ObjectMapper objectMapper, SmsVerificationService sms,
                             UserAccountMapper accounts, UserCredentialMapper credentials,
                             AuthService authService, FileStorageService fileStorageService) {
        this.properties = properties; this.stateStore = stateStore; this.objectMapper = objectMapper;
        this.sms = sms; this.accounts = accounts; this.credentials = credentials; this.authService = authService;
        this.fileStorageService = fileStorageService;
    }

    public Map<String, Object> startWechat(String code, HttpServletRequest httpReq) {
        if (!properties.getWechat().isEnabled()) throw new BusinessException(50312, "微信登录尚未开通");
        try {
            SocialProfile profile = exchangeWechatCode(code);
            UserCredential credential = credentials.selectOne(new LambdaQueryWrapper<UserCredential>()
                    .eq(UserCredential::getCredType, "wechat")
                    .eq(UserCredential::getIdentifierHash, HashUtils.sha256Hex(profile.providerId())));
            if (credential != null) {
                return Map.of("status", "login", "token",
                        authService.issueTokensForSocial(credential.getUserId(), httpReq));
            }
            String ticket = ticket();
            stateStore.put(key(ticket), objectMapper.writeValueAsString(profile), TICKET_TTL);
            return Map.of("status", "phone_required", "ticket", ticket,
                    "nickname", profile.nickname(), "avatarUrl", profile.avatarUrl());
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw new BusinessException(50212, "微信授权失败，请重试"); }
    }

    @Transactional
    public TokenResponse verifyPhone(SocialPhoneVerifyRequest req, HttpServletRequest httpReq) {
        String phone = req.getPhone().replaceAll("\\D", "");
        if (!phone.matches("^1\\d{10}$")) throw new BusinessException(40003, "手机号格式不正确");
        sms.verifyPhoneCode(SmsVerificationService.SCENE_AUTH, phone, req.getCode());
        String raw = stateStore.take(key(req.getTicket()));
        if (raw == null) throw new BusinessException(40001, "授权已过期，请重新登录");
        try {
            SocialProfile profile = objectMapper.readValue(raw, SocialProfile.class);
            String providerHash = HashUtils.sha256Hex(profile.providerId());
            UserCredential social = credentials.selectOne(new LambdaQueryWrapper<UserCredential>()
                    .eq(UserCredential::getCredType, profile.provider()).eq(UserCredential::getIdentifierHash, providerHash));
            UserCredential phoneCredential = credentials.selectOne(new LambdaQueryWrapper<UserCredential>()
                    .eq(UserCredential::getCredType, "phone").eq(UserCredential::getIdentifierHash, HashUtils.sha256Hex(phone)));
            String userId;
            if (social != null && phoneCredential != null && !social.getUserId().equals(phoneCredential.getUserId()))
                throw new BusinessException(40902, "该第三方账号和手机号已绑定到不同账号");
            if (phoneCredential != null) {
                userId = phoneCredential.getUserId();
            } else if (social != null) {
                userId = social.getUserId();
                UserCredential phoneCredentialToInsert = new UserCredential(); phoneCredentialToInsert.setUserId(userId); phoneCredentialToInsert.setCredType("phone");
                phoneCredentialToInsert.setIdentifierHash(HashUtils.sha256Hex(phone)); phoneCredentialToInsert.setSecretHash("");
                phoneCredentialToInsert.setCreatedAt(LocalDateTime.now()); phoneCredentialToInsert.setUpdatedAt(LocalDateTime.now()); credentials.insert(phoneCredentialToInsert);
            } else {
                userId = authService.createPhoneAccount(phone, "健康用户", req.getAgreementVersion());
            }
            if (social == null) {
                social = new UserCredential(); social.setUserId(userId); social.setCredType(profile.provider());
                social.setIdentifierHash(providerHash); social.setSecretHash(""); social.setCreatedAt(LocalDateTime.now()); social.setUpdatedAt(LocalDateTime.now()); credentials.insert(social);
            }
            if (req.isSyncProfile()) {
                String avatarUrl = importAvatar(profile.avatarUrl(), userId);
                var update = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserAccount>()
                        .eq(UserAccount::getUserId, userId).set(UserAccount::getNickname, boundedNickname(profile.nickname()));
                if (!avatarUrl.isBlank()) update.set(UserAccount::getAvatarUrl, avatarUrl);
                accounts.update(null, update);
            }
            return authService.issueTokensForSocial(userId, httpReq);
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw new BusinessException(50012, "第三方账号绑定失败"); }
    }

    private SocialProfile exchangeWechatCode(String code) throws Exception {
        var w = properties.getWechat();
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + enc(w.getAppId()) + "&secret=" + enc(w.getAppSecret()) + "&code=" + enc(code) + "&grant_type=authorization_code";
        JsonNode token = objectMapper.readTree(http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        if (token.has("errcode")) throw new BusinessException(50212, "微信授权失败");
        String access = token.path("access_token").asText(); String openid = token.path("openid").asText();
        if (access.isBlank() || openid.isBlank()) throw new BusinessException(50212, "微信授权失败");
        String infoUrl = "https://api.weixin.qq.com/sns/userinfo?access_token=" + enc(access) + "&openid=" + enc(openid) + "&lang=zh_CN";
        JsonNode info = objectMapper.readTree(http.send(HttpRequest.newBuilder(URI.create(infoUrl)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        if (info.has("errcode")) throw new BusinessException(50212, "微信用户信息获取失败");
        return new SocialProfile("wechat", openid, boundedNickname(info.path("nickname").asText("健康用户")), info.path("headimgurl").asText(""));
    }

    private String importAvatar(String url, String userId) {
        if (url == null || url.isBlank()) return "";
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equals("qlogo.cn") || host.endsWith(".qlogo.cn"))) return "";
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) return "";
            String type = response.headers().firstValue("content-type").orElse("image/jpeg").split(";")[0];
            return fileStorageService.storeRemoteAvatar(response.body(), type, userId);
        } catch (Exception ignored) { return ""; }
    }

    private String ticket() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private String key(String ticket) { return "hrp:auth:social-ticket:" + ticket; }
    private String boundedNickname(String value) {
        String nickname = value == null || value.isBlank() ? "健康用户" : value.trim();
        return nickname.length() > 64 ? nickname.substring(0, 64) : nickname;
    }
    private String enc(String value) { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
    private record SocialProfile(String provider, String providerId, String nickname, String avatarUrl) {}
}
