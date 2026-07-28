package io.healthresetplan.modules.data.dto;

import java.util.Map;

public record UserDataResponse(long version, Map<String, Object> data) {
}
