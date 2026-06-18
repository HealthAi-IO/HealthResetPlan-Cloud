package io.healthresetplan.modules.admin.auth.dto;

public record AdminTokenResponse(
        String accessToken,
        String refreshToken,
        long accessExpiresIn,
        long adminId,
        String username,
        String nickname,
        String roleCode,
        String permissions
) {}

