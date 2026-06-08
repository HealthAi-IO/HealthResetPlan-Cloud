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

    /**
     * 上传用户头像。
     * 非加密、公开可读，限制 2MB，只接受常见图片格式。
     */
    @PostMapping("/avatar")
    public R<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail(40001, "文件不能为空");
        }
        if (file.getSize() > 2 * 1024 * 1024L) {
            return R.fail(40001, "头像不能超过 2MB");
        }
        String original = file.getOriginalFilename();
        String ext = ".jpg";
        if (original != null) {
            String lower = original.toLowerCase();
            if (lower.endsWith(".png")) ext = ".png";
            else if (lower.endsWith(".webp")) ext = ".webp";
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ext = ".jpg";
            else if (lower.endsWith(".gif")) ext = ".gif";
            else return R.fail(40001, "仅支持 jpg、png、webp、gif 图片");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.startsWith("image/")) {
            return R.fail(40001, "仅支持图片格式");
        }

        String userId = currentUserId();
        String avatarKey = "avatars/" + userId + ext;
        storageService.storeAvatar(file, avatarKey);
        return R.ok(Map.of("avatarUrl", avatarKey));
    }

    /**
     * 获取头像图片（公开可读）。
     * 客户端直接用 Image.network(http://host/api/v1/files/avatar/{userId}) 展示。
     */
    @GetMapping("/avatar/{userId}")
    public void serveAvatar(@PathVariable String userId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        // 尝试 .jpg / .png / .webp
        String[] exts = {".jpg", ".png", ".webp", ".gif"};
        byte[] data = null;
        String foundExt = null;
        for (String ext : exts) {
            data = storageService.readAvatar("avatars/" + userId + ext);
            if (data != null) {
                foundExt = ext;
                break;
            }
        }
        if (data == null) {
            response.sendError(404);
            return;
        }
        String contentType = switch (foundExt) {
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            case ".gif" -> "image/gif";
            default -> "image/jpeg";
        };
        response.setContentType(contentType);
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
