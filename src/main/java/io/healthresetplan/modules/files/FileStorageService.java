package io.healthresetplan.modules.files;

import io.healthresetplan.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 加密文件存储服务。
 *
 * <p>当前实现：本地文件系统，路径 = {@code {storagePath}/{userId}/{clientId}.enc}。</p>
 * <p>后续换 OSS 只需替换此类的 store / getUrl 方法，调用方不变。</p>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${app.files.storage-path:/home/lyt/hrp-uploads}")
    private String storagePath;

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(Paths.get(storagePath));
        log.info("FileStorageService 初始化，存储根目录：{}", storagePath);
    }

    /**
     * 存储加密文件。
     *
     * @param file     MultipartFile（加密后的二进制内容）
     * @param userId   所属用户 ID
     * @param clientId 客户端生成的唯一 ID，作为文件名（已含后缀 .enc）
     * @return ossKey  文件在存储中的相对路径，如 reports/userId/clientId.enc
     */
    public String store(MultipartFile file, String userId, String clientId) {
        if (file.isEmpty()) {
            throw new BusinessException(40001, "上传文件不能为空");
        }
        if (file.getSize() > 20 * 1024 * 1024L) {
            throw new BusinessException(40001, "文件大小不能超过 20MB（加密后）");
        }

        // 文件名强制为 clientId.enc，防止路径遍历
        String safeClientId = clientId.replaceAll("[^a-zA-Z0-9\\-_]", "");
        String ossKey = "reports/" + userId + "/" + safeClientId + ".enc";

        try {
            Path dir = Paths.get(storagePath, "reports", userId);
            Files.createDirectories(dir);
            Path dest = dir.resolve(safeClientId + ".enc");
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件存储成功 userId={} ossKey={} size={}B",
                    userId, ossKey, file.getSize());
            return ossKey;
        } catch (IOException e) {
            log.error("文件存储失败 userId={} clientId={}", userId, clientId, e);
            throw new BusinessException(50001, "文件存储失败，请重试");
        }
    }

    /** 删除文件（报告删除时联动调用）。 */
    public void delete(String ossKey) {
        try {
            Path file = Paths.get(storagePath, ossKey);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("文件删除失败 ossKey={}", ossKey, e);
        }
    }
}
