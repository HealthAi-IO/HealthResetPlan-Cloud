package io.healthresetplan.modules.content;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.content.dto.ContentTaskRequest;
import io.healthresetplan.modules.content.dto.ContentUpsertRequest;
import io.healthresetplan.modules.files.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/health-content")
public class AdminContentController {

    private final ContentService contentService;
    private final ContentTaskService taskService;
    private final AiContentGenerationService generationService;
    private final FileStorageService fileStorageService;

    public AdminContentController(
            ContentService contentService,
            ContentTaskService taskService,
            AiContentGenerationService generationService,
            FileStorageService fileStorageService) {
        this.contentService = contentService;
        this.taskService = taskService;
        this.generationService = generationService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(contentService.adminList(type, status, page, size));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable long id) {
        return R.ok(contentService.adminDetail(id));
    }

    @PostMapping
    public R<Map<String, Long>> create(
            @Valid @RequestBody ContentUpsertRequest request,
            Authentication authentication) {
        return R.ok(Map.of("id", contentService.create(request, adminId(authentication))));
    }

    @PutMapping("/{id}")
    public R<Void> update(
            @PathVariable long id,
            @Valid @RequestBody ContentUpsertRequest request) {
        contentService.update(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable long id, Authentication authentication) {
        contentService.publish(id, adminId(authentication));
        return R.ok();
    }

    @PostMapping("/{id}/offline")
    public R<Void> offline(@PathVariable long id) {
        contentService.takeOffline(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable long id) {
        contentService.deleteDraft(id);
        return R.ok();
    }

    @PostMapping("/assets")
    public R<Map<String, String>> uploadAsset(@RequestParam("file") MultipartFile file) {
        String objectKey = fileStorageService.storeImage(file, "content", UUID.randomUUID().toString());
        String contentType = file.getContentType() == null ? "image/jpeg" : file.getContentType();
        String url = "/api/v1/content/assets?objectKey="
                + URLEncoder.encode(objectKey, StandardCharsets.UTF_8)
                + "&contentType=" + URLEncoder.encode(contentType, StandardCharsets.UTF_8);
        return R.ok(Map.of("url", url, "objectKey", objectKey));
    }

    @GetMapping("/tasks")
    public R<List<Map<String, Object>>> tasks() {
        return R.ok(taskService.list());
    }

    @PostMapping("/tasks")
    public R<Map<String, Long>> createTask(
            @Valid @RequestBody ContentTaskRequest request,
            Authentication authentication) {
        return R.ok(Map.of("id", taskService.create(request, adminId(authentication))));
    }

    @PutMapping("/tasks/{id}")
    public R<Void> updateTask(
            @PathVariable long id,
            @Valid @RequestBody ContentTaskRequest request) {
        taskService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/tasks/{id}")
    public R<Void> deleteTask(@PathVariable long id) {
        taskService.delete(id);
        return R.ok();
    }

    @PostMapping("/tasks/{id}/generate")
    public R<Void> generate(@PathVariable long id) {
        taskService.get(id);
        generationService.generateAsync(id);
        return R.ok();
    }

    private long adminId(Authentication authentication) {
        String principal = authentication == null ? "" : String.valueOf(authentication.getPrincipal());
        if (!principal.startsWith("admin:")) return 0;
        return Long.parseLong(principal.substring("admin:".length()));
    }
}
