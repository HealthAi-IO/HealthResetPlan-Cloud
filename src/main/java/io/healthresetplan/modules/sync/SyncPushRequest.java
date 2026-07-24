package io.healthresetplan.modules.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record SyncPushRequest(
        @NotBlank String deviceId,
        String keyFingerprint,
        @NotNull List<Item> items
) {
    public record Item(
            @NotBlank String table,
            @NotBlank String clientId,
            long version,
            long clientUpdatedAt,
            String cipher,
            String iv,
            String tag,
            String alg,
            Boolean deleted,
            Boolean keyMigration,
            Map<String, Object> meta
    ) {
    }
}
