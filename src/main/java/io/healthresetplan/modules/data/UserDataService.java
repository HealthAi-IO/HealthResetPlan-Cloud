package io.healthresetplan.modules.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.crypto.DataEncryptionService;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.data.dto.UserDataResponse;
import io.healthresetplan.modules.data.dto.UserDataSaveRequest;
import io.healthresetplan.modules.data.entity.UserDataState;
import io.healthresetplan.modules.data.mapper.UserDataStateMapper;
import io.healthresetplan.modules.files.FileStorageService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserDataService {

    private static final int MAX_JSON_LENGTH = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "user_profile", "health_indicator", "plan", "clock_record", "reminder",
            "health_report", "ai_weekly_report", "meal_record", "meal_recipe", "meal_settings", "ai_session", "ai_message", "ai_memory",
            "quit_smoking_profile", "smoking_event");

    private final UserDataStateMapper mapper;
    private final DataEncryptionService encryption;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorage;

    public UserDataService(
            UserDataStateMapper mapper,
            DataEncryptionService encryption,
            ObjectMapper objectMapper,
            FileStorageService fileStorage) {
        this.mapper = mapper;
        this.encryption = encryption;
        this.objectMapper = objectMapper;
        this.fileStorage = fileStorage;
    }

    public UserDataResponse load(String userId) {
        UserDataState state = mapper.selectById(userId);
        if (state == null) {
            return new UserDataResponse(0, emptyData());
        }
        try {
            String json = encryption.decryptText(
                    state.getPayloadCipher(),
                    state.getPayloadNonce(),
                    state.getKeyVersion(),
                    aad(userId));
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<>() {});
            return new UserDataResponse(state.getVersion(), normalize(data));
        } catch (Exception ex) {
            throw new IllegalStateException("用户在线数据无法解密", ex);
        }
    }

    @Transactional
    public UserDataResponse save(String userId, UserDataSaveRequest request) {
        Map<String, Object> normalized = normalize(request.getData());
        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new BusinessException(40001, "业务数据格式无效");
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new BusinessException(40001, "业务数据超过 10MB 限制");
        }
        DataEncryptionService.EncryptedText encrypted = encryption.encryptText(json, aad(userId));

        if (request.getVersion() == 0) {
            UserDataState state = new UserDataState();
            state.setUserId(userId);
            state.setPayloadCipher(encrypted.ciphertext());
            state.setPayloadNonce(encrypted.nonce());
            state.setKeyVersion(encrypted.keyVersion());
            state.setVersion(1L);
            state.setCreatedAt(LocalDateTime.now());
            state.setUpdatedAt(LocalDateTime.now());
            try {
                mapper.insert(state);
                return new UserDataResponse(1, normalized);
            } catch (DuplicateKeyException ignored) {
                throw new BusinessException(40901, "数据已在其他设备更新，请重新加载");
            }
        }

        int updated = mapper.updateIfVersionMatches(
                userId,
                encrypted.ciphertext(),
                encrypted.nonce(),
                encrypted.keyVersion(),
                request.getVersion());
        if (updated != 1) {
            throw new BusinessException(40901, "数据已在其他设备更新，请重新加载");
        }
        return new UserDataResponse(request.getVersion() + 1, normalized);
    }

    public void delete(String userId) {
        deleteObjectKeys(load(userId).data(), userId);
        mapper.deleteById(userId);
    }

    private void deleteObjectKeys(Object value, String userId) {
        if (value instanceof String text
                && (text.startsWith("files/" + userId + "/") || text.startsWith("avatars/" + userId + "/"))) {
            fileStorage.delete(text, userId);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> deleteObjectKeys(item, userId));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> deleteObjectKeys(item, userId));
        }
    }

    private Map<String, Object> normalize(Map<String, Object> source) {
        Map<String, Object> normalized = emptyData();
        if (source == null) {
            return normalized;
        }
        for (String key : ALLOWED_TABLES) {
            Object rows = source.get(key);
            if (rows instanceof List<?>) {
                normalized.put(key, rows);
            }
        }
        return normalized;
    }

    private Map<String, Object> emptyData() {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String table : ALLOWED_TABLES) {
            data.put(table, List.of());
        }
        return data;
    }

    private String aad(String userId) {
        return "user-data:" + userId;
    }
}
