package io.healthresetplan.modules.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record SyncPushRequest(
        @NotBlank String deviceId,
        @NotNull List<Item> items
) {
    public record Item(
            @NotBlank String table,
            @NotBlank String clientId,
            long version,
            long clientUpdatedAt,
            @NotBlank String cipher,
            @NotBlank String iv,
            @NotBlank String tag,
            String alg,
            Map<String, Object> meta
    ) {
    }
}
