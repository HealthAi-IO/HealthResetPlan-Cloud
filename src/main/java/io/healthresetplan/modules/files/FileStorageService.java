package io.healthresetplan.modules.files;

import io.healthresetplan.common.crypto.DataEncryptionService;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.config.OssProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024L;
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;

    private final OssProperties properties;
    private final DataEncryptionService encryption;
    private final Path localRoot;
    private volatile S3Client client;

    public FileStorageService(OssProperties properties, DataEncryptionService encryption) {
        this.properties = properties;
        this.encryption = encryption;
        this.localRoot = Path.of(properties.getLocalPath()).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, String userId, String clientId) {
        validate(file, MAX_FILE_SIZE);
        String safeClientId = safe(clientId);
        String objectKey = "files/" + userId + "/" + safeClientId + ".enc";
        putEncrypted(objectKey, bytes(file), userId);
        return objectKey;
    }

    public String storeImage(MultipartFile file, String userId, String clientId) {
        validate(file, MAX_IMAGE_SIZE);
        byte[] data = bytes(file);
        validateImage(file.getContentType(), data);
        String safeClientId = safe(clientId);
        String objectKey = "files/" + userId + "/" + safeClientId + ".enc";
        putEncrypted(objectKey, data, userId);
        return objectKey;
    }

    public String storeAvatar(MultipartFile file, String userId) {
        validate(file, 2 * 1024 * 1024L);
        byte[] data = bytes(file);
        validateImage(file.getContentType(), data);
        String extension = imageExtension(file.getContentType());
        String objectKey = "avatars/" + userId + "/" + java.util.UUID.randomUUID() + extension + ".enc";
        putEncrypted(objectKey, data, userId);
        return objectKey;
    }

    public String storeRemoteAvatar(byte[] data, String contentType, String userId) {
        if (data == null || data.length == 0 || data.length > 2 * 1024 * 1024L) {
            throw new BusinessException(40001, "第三方头像大小无效");
        }
        validateImage(contentType, data);
        String extension = imageExtension(contentType);
        String objectKey = "avatars/" + userId + "/" + java.util.UUID.randomUUID() + extension + ".enc";
        putEncrypted(objectKey, data, userId);
        return "/api/v1/files/avatar?objectKey="
                + URLEncoder.encode(objectKey, StandardCharsets.UTF_8);
    }

    public String canonicalAvatarUrl(String avatarUrl, String userId) {
        String objectKey = avatarObjectKey(avatarUrl);
        requireAvatarOwnership(objectKey, userId);
        return "/api/v1/files/avatar?objectKey="
                + URLEncoder.encode(objectKey, StandardCharsets.UTF_8);
    }

    public String avatarObjectKey(String avatarUrl) {
        try {
            URI uri = URI.create(avatarUrl == null ? "" : avatarUrl.trim());
            String path = uri.getPath();
            if (!("/api/v1/files/avatar".equals(path) || "/api/v1/files/content".equals(path))) {
                throw new BusinessException(40001, "头像地址无效");
            }
            String query = uri.getRawQuery();
            if (query == null) throw new BusinessException(40001, "头像地址无效");
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2 && "objectKey".equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                    return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(40001, "头像地址无效");
        }
        throw new BusinessException(40001, "头像地址无效");
    }

    public void requireAvatarOwnership(String objectKey, String userId) {
        String prefix = "avatars/" + userId + "/";
        if (objectKey == null || !objectKey.startsWith(prefix)
                || objectKey.contains("..")
                || !objectKey.matches("^avatars/[^/]+/[0-9a-fA-F-]+\\.(jpg|png|webp|gif)\\.enc$")) {
            throw new BusinessException(40301, "无权使用该头像");
        }
    }

    public String avatarContentType(String objectKey) {
        if (objectKey != null && objectKey.endsWith(".png.enc")) return "image/png";
        if (objectKey != null && objectKey.endsWith(".webp.enc")) return "image/webp";
        if (objectKey != null && objectKey.endsWith(".gif.enc")) return "image/gif";
        return "image/jpeg";
    }

    public byte[] read(String objectKey, String userId) {
        requireOwnership(objectKey, userId);
        if (useLocalStorage()) {
            Path path = localPath(objectKey);
            if (!Files.exists(path)) return null;
            try {
                return encryption.decryptFile(Files.readAllBytes(path), fileAad(userId, objectKey));
            } catch (Exception ex) {
                throw storageFailure("文件读取失败", ex);
            }
        }
        try {
            byte[] encrypted = client().getObjectAsBytes(builder -> builder
                    .bucket(properties.getBucket())
                    .key(objectKey)).asByteArray();
            return encryption.decryptFile(encrypted, fileAad(userId, objectKey));
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return null;
            }
            throw storageFailure("文件读取失败", ex);
        }
    }

    public byte[] readAvatar(String objectKey) {
        if (objectKey == null || !objectKey.startsWith("avatars/")) {
            throw new BusinessException(40001, "头像路径无效");
        }
        String[] parts = objectKey.split("/", 3);
        if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) {
            throw new BusinessException(40001, "头像路径无效");
        }
        return read(objectKey, parts[1]);
    }

    public void delete(String objectKey, String userId) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        requireOwnership(objectKey, userId);
        if (useLocalStorage()) {
            try {
                Files.deleteIfExists(localPath(objectKey));
            } catch (Exception ex) {
                log.warn("本地文件删除失败 objectKey={}", objectKey, ex);
            }
            return;
        }
        try {
            client().deleteObject(builder -> builder.bucket(properties.getBucket()).key(objectKey));
        } catch (S3Exception ex) {
            log.warn("OSS 文件删除失败 objectKey={}", objectKey, ex);
        }
    }

    private void putEncrypted(String objectKey, byte[] plaintext, String userId) {
        byte[] encrypted = encryption.encryptFile(plaintext, fileAad(userId, objectKey));
        if (useLocalStorage()) {
            Path path = localPath(objectKey);
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, encrypted);
                return;
            } catch (Exception ex) {
                throw storageFailure("文件上传失败，请重试", ex);
            }
        }
        try {
            client().putObject(
                    builder -> builder
                            .bucket(properties.getBucket())
                            .key(objectKey)
                            .contentType("application/octet-stream"),
                    RequestBody.fromBytes(encrypted));
        } catch (S3Exception ex) {
            throw storageFailure("文件上传失败，请重试", ex);
        }
    }

    private boolean useLocalStorage() {
        return blank(properties.getAccessKeyId()) || blank(properties.getSecretAccessKey());
    }

    private Path localPath(String objectKey) {
        Path path = localRoot.resolve(objectKey).normalize();
        if (!path.startsWith(localRoot)) {
            throw new BusinessException(40001, "文件路径无效");
        }
        return path;
    }

    private S3Client client() {
        S3Client existing = client;
        if (existing != null) {
            return existing;
        }
        if (blank(properties.getAccessKeyId()) || blank(properties.getSecretAccessKey())) {
            throw new IllegalStateException("缺少京东云 OSS 访问密钥");
        }
        synchronized (this) {
            if (client == null) {
                client = S3Client.builder()
                        .endpointOverride(URI.create(properties.getEndpoint()))
                        .region(Region.of(properties.getRegion()))
                        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                                properties.getAccessKeyId(), properties.getSecretAccessKey())))
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .build();
            }
            return client;
        }
    }

    private void validate(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "上传文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(40001, "文件大小超过限制");
        }
    }

    private byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(50001, "文件读取失败");
        }
    }

    private void validateImage(String contentType, byte[] data) {
        boolean jpeg = data.length >= 3
                && (data[0] & 0xff) == 0xff
                && (data[1] & 0xff) == 0xd8
                && (data[2] & 0xff) == 0xff;
        boolean png = data.length >= 8
                && (data[0] & 0xff) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4e
                && data[3] == 0x47
                && data[4] == 0x0d
                && data[5] == 0x0a
                && data[6] == 0x1a
                && data[7] == 0x0a;
        boolean webp = data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P';
        boolean valid = ("image/jpeg".equalsIgnoreCase(contentType)
                || "image/jpg".equalsIgnoreCase(contentType)) && jpeg;
        valid |= "image/png".equalsIgnoreCase(contentType) && png;
        valid |= "image/webp".equalsIgnoreCase(contentType) && webp;
        if (!valid) {
            throw new BusinessException(40001, "仅支持 JPG、PNG 或 WebP 图片");
        }
    }

    private String imageExtension(String contentType) {
        if ("image/png".equalsIgnoreCase(contentType)) return ".png";
        if ("image/webp".equalsIgnoreCase(contentType)) return ".webp";
        if ("image/jpeg".equalsIgnoreCase(contentType)
                || "image/jpg".equalsIgnoreCase(contentType)) return ".jpg";
        throw new BusinessException(40001, "仅支持 JPG、PNG 或 WebP 图片");
    }

    private String safe(String value) {
        String safe = value == null ? "" : value.replaceAll("[^a-zA-Z0-9\\-_]", "");
        if (safe.isBlank()) {
            throw new BusinessException(40001, "clientId 无效");
        }
        return safe;
    }

    private void requireOwnership(String objectKey, String userId) {
        if (objectKey == null || !(objectKey.startsWith("files/" + userId + "/")
                || objectKey.startsWith("avatars/" + userId + "/"))) {
            throw new BusinessException(40301, "无权访问该文件");
        }
    }

    private String fileAad(String userId, String objectKey) {
        return "user-file:" + userId + ":" + objectKey;
    }

    private BusinessException storageFailure(String message, Exception ex) {
        log.error(message, ex);
        return new BusinessException(50001, message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
