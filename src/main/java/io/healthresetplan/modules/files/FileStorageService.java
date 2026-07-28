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

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024L;

    private final OssProperties properties;
    private final DataEncryptionService encryption;
    private volatile S3Client client;

    public FileStorageService(OssProperties properties, DataEncryptionService encryption) {
        this.properties = properties;
        this.encryption = encryption;
    }

    public String store(MultipartFile file, String userId, String clientId) {
        validate(file, MAX_FILE_SIZE);
        String safeClientId = safe(clientId);
        String objectKey = "files/" + userId + "/" + safeClientId + ".enc";
        putEncrypted(objectKey, bytes(file), userId);
        return objectKey;
    }

    public String storeAvatar(MultipartFile file, String userId, String extension) {
        validate(file, 2 * 1024 * 1024L);
        String objectKey = "avatars/" + userId + "/" + java.util.UUID.randomUUID() + extension + ".enc";
        putEncrypted(objectKey, bytes(file), userId);
        return objectKey;
    }

    public byte[] read(String objectKey, String userId) {
        requireOwnership(objectKey, userId);
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

    public void delete(String objectKey, String userId) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        requireOwnership(objectKey, userId);
        try {
            client().deleteObject(builder -> builder.bucket(properties.getBucket()).key(objectKey));
        } catch (S3Exception ex) {
            log.warn("OSS 文件删除失败 objectKey={}", objectKey, ex);
        }
    }

    private void putEncrypted(String objectKey, byte[] plaintext, String userId) {
        byte[] encrypted = encryption.encryptFile(plaintext, fileAad(userId, objectKey));
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
