package io.healthresetplan.modules.release;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.result.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/releases")
public class PublicReleaseController {

    private static final List<String> PLATFORMS =
            List.of("android", "ios", "windows", "macos", "web", "wechat");

    private final JdbcTemplate jdbc;

    public PublicReleaseController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/latest")
    public R<Map<String, Object>> latest(
            @RequestParam String platform,
            @RequestParam(required = false, defaultValue = "official") String channel) {
        String normalizedPlatform = normalize(platform);
        String normalizedChannel = normalize(channel);
        if (!PLATFORMS.contains(normalizedPlatform) || normalizedChannel.isBlank()) {
            throw new BusinessException(40001, "平台或发布渠道不合法");
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT version_name, package_size_mb, package_url,
                       COALESCE(released_at, updated_at) AS updated_at
                FROM app_release
                WHERE deleted_at IS NULL
                  AND status = 1
                  AND release_stage = 'release'
                  AND platform = ?
                  AND channel = ?
                ORDER BY version_code DESC, COALESCE(released_at, updated_at) DESC
                LIMIT 1
                """, normalizedPlatform, normalizedChannel);

        if (rows.isEmpty()) {
            return R.ok(Map.of(
                    "available", false,
                    "platform", normalizedPlatform
            ));
        }

        Map<String, Object> release = rows.get(0);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", true);
        response.put("platform", normalizedPlatform);
        response.put("version", release.get("version_name"));
        response.put("sizeMb", release.get("package_size_mb"));
        response.put("downloadUrl", release.get("package_url"));
        response.put("updatedAt", release.get("updated_at"));
        return R.ok(response);
    }

    @GetMapping("/check")
    public R<Map<String, Object>> check(
            @RequestParam String platform,
            @RequestParam String currentVersion,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String deviceId) {
        String normalizedPlatform = normalize(platform);
        String normalizedVersion = normalize(currentVersion);
        String normalizedChannel = normalize(channel);
        if (!PLATFORMS.contains(normalizedPlatform) || normalizedVersion.isBlank()) {
            throw new BusinessException(40001, "平台或当前版本不合法");
        }

        List<Object> arguments = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                WHERE deleted_at IS NULL
                  AND status = 1
                  AND release_stage IN ('gray', 'release')
                  AND platform = ?
                """);
        arguments.add(normalizedPlatform);
        if (!normalizedChannel.isBlank()) {
            where.append(" AND channel = ? ");
            arguments.add(normalizedChannel);
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, platform, channel, version_name, version_code, release_stage,
                       is_force_update, rollout_percent, package_url, package_size_mb,
                       min_supported_version, release_notes, released_at
                FROM app_release
                """ + where + """
                ORDER BY version_code DESC, COALESCE(released_at, created_at) DESC
                LIMIT 1
                """, arguments.toArray());

        if (rows.isEmpty()) {
            return R.ok(Map.of(
                    "hasUpdate", false,
                    "forceUpdate", false,
                    "eligible", false,
                    "platform", normalizedPlatform,
                    "currentVersion", normalizedVersion
            ));
        }

        Map<String, Object> release = rows.get(0);
        String latestVersion = text(release.get("version_name"));
        String minimumVersion = text(release.get("min_supported_version"));
        boolean hasUpdate = compareVersion(normalizedVersion, latestVersion) < 0;
        boolean belowMinimum = !minimumVersion.isBlank()
                && compareVersion(normalizedVersion, minimumVersion) < 0;
        int rolloutPercent = number(release.get("rollout_percent"));
        boolean eligible = !"gray".equals(text(release.get("release_stage")))
                || rolloutPercent >= 100
                || rolloutBucket(deviceId) < rolloutPercent;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hasUpdate", hasUpdate && eligible);
        response.put("forceUpdate", hasUpdate && (belowMinimum || number(release.get("is_force_update")) == 1));
        response.put("eligible", eligible);
        response.put("platform", normalizedPlatform);
        response.put("currentVersion", normalizedVersion);
        response.put("latestVersion", latestVersion);
        response.put("minimumSupportedVersion", minimumVersion);
        response.put("channel", release.get("channel"));
        response.put("packageUrl", release.get("package_url"));
        response.put("packageSizeMb", release.get("package_size_mb"));
        response.put("releaseNotes", release.get("release_notes"));
        response.put("releasedAt", release.get("released_at"));
        response.put("rolloutPercent", rolloutPercent);
        return R.ok(response);
    }

    private int rolloutBucket(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return 0;
        return Math.floorMod(deviceId.hashCode(), 100);
    }

    private int compareVersion(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = index < leftParts.length ? numericPart(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? numericPart(rightParts[index]) : 0;
            if (leftValue != rightValue) return Integer.compare(leftValue, rightValue);
        }
        return 0;
    }

    private int numericPart(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
