package io.healthresetplan.modules.sync;

import java.util.Map;

/** 单条增量拉取结果，结构与 SyncPushRequest.Item 镜像，cipher/iv/tag 均为客户端密文。 */
public record SyncPullItem(
        String table,
        String clientId,
        long version,
        long clientUpdatedAt,
        String cipher,
        String iv,
        String tag,
        String alg,
        Map<String, Object> meta
) {}
