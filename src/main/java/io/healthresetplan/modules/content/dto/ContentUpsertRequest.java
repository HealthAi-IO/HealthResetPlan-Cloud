package io.healthresetplan.modules.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContentUpsertRequest(
        @NotBlank String type,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 500) String summary,
        @Size(max = 1024) String coverUrl,
        @Size(max = 1000) String coverPrompt,
        String bodyHtml,
        Object content,
        String status
) {
}
