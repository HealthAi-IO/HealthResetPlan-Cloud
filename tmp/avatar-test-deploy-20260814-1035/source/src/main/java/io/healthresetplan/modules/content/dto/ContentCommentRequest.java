package io.healthresetplan.modules.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContentCommentRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 500, message = "评论不能超过500字")
        String content) {
}
