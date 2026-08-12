package io.healthresetplan.modules.content.dto;

import jakarta.validation.constraints.Pattern;

public record ContentReactionRequest(
        @Pattern(regexp = "^(like|dislike)?$", message = "互动类型不正确")
        String reaction) {
}
