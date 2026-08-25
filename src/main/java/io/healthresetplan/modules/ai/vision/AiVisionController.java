package io.healthresetplan.modules.ai.vision;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.result.R;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/vision")
public class AiVisionController {

    private final AiVisionService service;
    private final io.healthresetplan.modules.ai.AiConsentService consentService;

    public AiVisionController(AiVisionService service, io.healthresetplan.modules.ai.AiConsentService consentService) {
        this.service = service;
        this.consentService = consentService;
    }

    @PostMapping("/analyze")
    public R<Map<String, Object>> analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        String userId = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        consentService.requireActive(userId);
        return R.ok(service.analyze(userId, file, type));
    }

    @PostMapping("/analyze-stored")
    public R<Map<String, Object>> analyzeStored(@RequestBody StoredAnalyzeRequest request) {
        String userId = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        consentService.requireActive(userId);
        return R.ok(service.analyzeStored(
                userId, request.objectKey(), request.mimeType(), request.type()));
    }

    public record StoredAnalyzeRequest(String objectKey, String mimeType, String type) {}
}
