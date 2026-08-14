package io.healthresetplan.modules.push;

import io.healthresetplan.common.crypto.DataEncryptionService;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.push.dto.WebPushSubscriptionRequest;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.apache.http.HttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import io.healthresetplan.config.WebPushProperties;

@Service
public class WebPushSubscriptionService {
    private final JdbcTemplate jdbcTemplate;
    private final DataEncryptionService encryption;
    private final WebPushProperties properties;
    private PushService pushService;

    public WebPushSubscriptionService(
            JdbcTemplate jdbcTemplate,
            DataEncryptionService encryption,
            WebPushProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryption = encryption;
        this.properties = properties;
    }

    @Transactional
    public void subscribe(String userId, String deviceId, WebPushSubscriptionRequest request) {
        validate(userId, deviceId, request);
        String aad = aad(userId, deviceId);
        var endpoint = encryption.encryptText(request.endpoint(), aad + ":endpoint");
        var p256dh = encryption.encryptText(request.p256dh(), aad + ":p256dh");
        var auth = encryption.encryptText(request.auth(), aad + ":auth");
        String endpointHash = sha256(request.endpoint());
        jdbcTemplate.update("DELETE FROM web_push_subscription WHERE endpoint_hash = ? AND (user_id <> ? OR device_id <> ?)",
                endpointHash, userId, deviceId);
        jdbcTemplate.update("""
                INSERT INTO web_push_subscription (
                    user_id, device_id, endpoint_hash,
                    endpoint_cipher, endpoint_nonce, endpoint_key_version,
                    p256dh_cipher, p256dh_nonce, p256dh_key_version,
                    auth_cipher, auth_nonce, auth_key_version, timezone, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                    endpoint_hash = VALUES(endpoint_hash),
                    endpoint_cipher = VALUES(endpoint_cipher), endpoint_nonce = VALUES(endpoint_nonce),
                    endpoint_key_version = VALUES(endpoint_key_version),
                    p256dh_cipher = VALUES(p256dh_cipher), p256dh_nonce = VALUES(p256dh_nonce),
                    p256dh_key_version = VALUES(p256dh_key_version),
                    auth_cipher = VALUES(auth_cipher), auth_nonce = VALUES(auth_nonce),
                    auth_key_version = VALUES(auth_key_version), timezone = VALUES(timezone),
                    status = 1, failure_count = 0
                """,
                userId, deviceId, endpointHash,
                endpoint.ciphertext(), endpoint.nonce(), endpoint.keyVersion(),
                p256dh.ciphertext(), p256dh.nonce(), p256dh.keyVersion(),
                auth.ciphertext(), auth.nonce(), auth.keyVersion(), request.timezone());
    }

    public void unsubscribe(String userId, String deviceId) {
        jdbcTemplate.update("DELETE FROM web_push_subscription WHERE user_id = ? AND device_id = ?", userId, deviceId);
    }

    public List<Subscription> activeSubscriptions() {
        return jdbcTemplate.queryForList("SELECT * FROM web_push_subscription WHERE status = 1")
                .stream().map(this::decrypt).toList();
    }

    public boolean alreadyDelivered(long subscriptionId, String minuteKey) {
        String key = sha256(subscriptionId + ":" + minuteKey);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM web_push_delivery WHERE occurrence_key = ?", Integer.class, key);
        return count != null && count > 0;
    }

    public void send(Subscription subscription, String minuteKey) throws Exception {
        if (!properties.isEnabled()) return;
        String payload = "{\"title\":\"健康重启计划提醒\","
                + "\"body\":\"你有一项已设定的健康提醒\",\"url\":\"/clock\"}";
        HttpResponse response = pushService().send(new Notification(
                subscription.endpoint(), subscription.p256dh(), subscription.auth(), payload));
        int status = response.getStatusLine().getStatusCode();
        if (status >= 200 && status < 300) {
            String key = sha256(subscription.id() + ":" + minuteKey);
            jdbcTemplate.update("INSERT IGNORE INTO web_push_delivery (occurrence_key, subscription_id) VALUES (?, ?)",
                    key, subscription.id());
            jdbcTemplate.update("UPDATE web_push_subscription SET failure_count = 0, last_success_at = NOW(3) WHERE id = ?",
                    subscription.id());
            return;
        }
        if (status == 404 || status == 410) {
            jdbcTemplate.update("UPDATE web_push_subscription SET status = 0 WHERE id = ?", subscription.id());
        } else {
            jdbcTemplate.update("UPDATE web_push_subscription SET failure_count = failure_count + 1 WHERE id = ?",
                    subscription.id());
        }
        throw new IllegalStateException("Web Push 返回 HTTP " + status);
    }

    public void cleanupDeliveries() {
        jdbcTemplate.update("DELETE FROM web_push_delivery WHERE sent_at < NOW() - INTERVAL 30 DAY");
    }

    private Subscription decrypt(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        String userId = String.valueOf(row.get("user_id"));
        String deviceId = String.valueOf(row.get("device_id"));
        String aad = aad(userId, deviceId);
        return new Subscription(
                id,
                userId,
                encryption.decryptText(String.valueOf(row.get("endpoint_cipher")),
                        String.valueOf(row.get("endpoint_nonce")),
                        ((Number) row.get("endpoint_key_version")).intValue(), aad + ":endpoint"),
                encryption.decryptText(String.valueOf(row.get("p256dh_cipher")),
                        String.valueOf(row.get("p256dh_nonce")),
                        ((Number) row.get("p256dh_key_version")).intValue(), aad + ":p256dh"),
                encryption.decryptText(String.valueOf(row.get("auth_cipher")),
                        String.valueOf(row.get("auth_nonce")),
                        ((Number) row.get("auth_key_version")).intValue(), aad + ":auth"),
                String.valueOf(row.get("timezone")));
    }

    private synchronized PushService pushService() throws Exception {
        if (pushService == null) {
            if (properties.getPublicKey().isBlank() || properties.getPrivateKey().isBlank()) {
                throw new IllegalStateException("Web Push VAPID 密钥未配置");
            }
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(
                    properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
        }
        return pushService;
    }

    private void validate(String userId, String deviceId, WebPushSubscriptionRequest request) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > 64) {
            throw new BusinessException(40001, "设备标识无效");
        }
        try {
            URI uri = URI.create(request.endpoint());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedPushHost(uri.getHost())) {
                throw new IllegalArgumentException();
            }
            if (Base64.getUrlDecoder().decode(padded(request.p256dh())).length != 65
                    || Base64.getUrlDecoder().decode(padded(request.auth())).length != 16) {
                throw new IllegalArgumentException();
            }
            java.time.ZoneId.of(request.timezone());
        } catch (Exception ex) {
            throw new BusinessException(40001, "推送订阅参数无效");
        }
    }

    static String aad(String userId, String deviceId) {
        return "web-push:" + userId + ":" + deviceId;
    }

    private boolean allowedPushHost(String host) {
        if (host == null) return false;
        String value = host.toLowerCase(java.util.Locale.ROOT);
        return value.equals("fcm.googleapis.com")
                || value.equals("updates.push.services.mozilla.com")
                || value.equals("web.push.apple.com")
                || value.endsWith(".push.apple.com")
                || value.endsWith(".notify.windows.com");
    }

    private String padded(String value) {
        return value + "=".repeat((4 - value.length() % 4) % 4);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record Subscription(long id, String userId, String endpoint, String p256dh, String auth, String timezone) {}
}
