package io.healthresetplan.modules.content;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.files.FileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private final ContentService contentService;
    private final FileStorageService fileStorageService;

    public ContentController(ContentService contentService, FileStorageService fileStorageService) {
        this.contentService = contentService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/content")
    public R<Map<String, Object>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return R.ok(contentService.listPublished(currentUserId(), type, page, size));
    }

    @GetMapping("/content/{id}")
    public R<Map<String, Object>> detail(@PathVariable long id) {
        return R.ok(contentService.publishedDetail(currentUserId(), id));
    }

    @PostMapping("/content/{id}/read")
    public R<Void> markRead(@PathVariable long id) {
        contentService.markRead(currentUserId(), id);
        return R.ok();
    }

    @GetMapping("/messages")
    public R<Map<String, Object>> messages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(contentService.listMessages(currentUserId(), page, size));
    }

    @GetMapping("/messages/unread-count")
    public R<Map<String, Long>> unreadCount() {
        return R.ok(Map.of("count", contentService.unreadMessageCount(currentUserId())));
    }

    @PostMapping("/messages/{id}/read")
    public R<Void> markMessageRead(@PathVariable long id) {
        contentService.markMessageRead(currentUserId(), id);
        return R.ok();
    }

    @PostMapping("/messages/read-all")
    public R<Void> markAllMessagesRead() {
        contentService.markAllMessagesRead(currentUserId());
        return R.ok();
    }

    @GetMapping("/content/assets")
    public void asset(
            @RequestParam String objectKey,
            @RequestParam(defaultValue = "image/jpeg") String contentType,
            HttpServletResponse response) throws IOException {
        byte[] data = fileStorageService.read(objectKey, "content");
        if (data == null) {
            response.sendError(404);
            return;
        }
        response.setContentType(contentType);
        response.setContentLength(data.length);
        response.setHeader("Cache-Control", "private, max-age=86400");
        response.getOutputStream().write(data);
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
