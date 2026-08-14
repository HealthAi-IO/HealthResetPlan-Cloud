package io.healthresetplan.modules.files;

import io.healthresetplan.common.result.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileStorageService storageService;

    public FileController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    public R<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientId") String clientId,
            @RequestParam(value = "kind", required = false) String kind) {
        String objectKey = "image".equals(kind)
                ? storageService.storeImage(file, currentUserId(), clientId)
                : storageService.store(file, currentUserId(), clientId);
        return R.ok(Map.of("objectKey", objectKey));
    }

    @PostMapping("/avatar")
    public R<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String objectKey = storageService.storeAvatar(file, currentUserId());
        return R.ok(Map.of(
                "objectKey", objectKey,
                "avatarUrl", storageService.canonicalAvatarUrl(
                        "/api/v1/files/avatar?objectKey=" + objectKey,
                        currentUserId())));
    }

    @GetMapping("/content")
    public void download(
            @RequestParam String objectKey,
            @RequestParam(defaultValue = "application/octet-stream") String contentType,
            HttpServletResponse response) throws IOException {
        byte[] data = storageService.read(objectKey, currentUserId());
        if (data == null) {
            response.sendError(404);
            return;
        }
        response.setContentType(contentType);
        response.setContentLength(data.length);
        response.setHeader("Cache-Control", "private, no-store");
        response.getOutputStream().write(data);
    }

    @GetMapping("/avatar")
    public void avatar(@RequestParam String objectKey, HttpServletResponse response) throws IOException {
        byte[] data = storageService.readAvatar(objectKey);
        if (data == null) {
            response.sendError(404);
            return;
        }
        response.setContentType(storageService.avatarContentType(objectKey));
        response.setContentLength(data.length);
        response.setHeader("Cache-Control", "private, max-age=300");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(data);
    }

    @DeleteMapping
    public R<Void> delete(@RequestParam String objectKey) {
        storageService.delete(objectKey, currentUserId());
        return R.ok();
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
