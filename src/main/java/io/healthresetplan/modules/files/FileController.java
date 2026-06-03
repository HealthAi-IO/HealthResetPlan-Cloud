package io.healthresetplan.modules.files;

import io.healthresetplan.common.result.R;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 加密文件上传接口。
 *
 * <p>客户端在本地用 DEK 加密原始文件后，将密文上传至此接口。
 * 服务端不解密，原样存储。DEK 的包裹密文单独通过 /api/v1/reports 保存。</p>
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileStorageService storageService;

    public FileController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 上传一个加密文件块。
     *
     * @param file     加密后的二进制文件
     * @param clientId 客户端生成的唯一 ID（UUID，与 ReportSaveRequest.clientId 一致）
     * @return ossKey  存储路径（保存到 ReportSaveRequest.imageOssKey）
     */
    @PostMapping("/upload")
    public R<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientId") String clientId) {

        String userId = currentUserId();
        String ossKey = storageService.store(file, userId, clientId);
        return R.ok(Map.of("ossKey", ossKey));
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
