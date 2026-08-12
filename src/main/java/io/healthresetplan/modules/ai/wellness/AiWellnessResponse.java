package io.healthresetplan.modules.ai.wellness;

import java.util.Map;

public record AiWellnessResponse(
        String provider,
        Map<String, Object> data
) {
}
