package io.healthresetplan.modules.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.healthresetplan.modules.sync.entity.SyncRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface SyncRecordMapper extends BaseMapper<SyncRecord> {

    @Select("SELECT * FROM sync_record " +
            "WHERE user_id = #{userId} AND table_name = #{tableName} AND client_id = #{clientId} " +
            "LIMIT 1")
    SyncRecord selectOneIncludingDeleted(
            @Param("userId") String userId,
            @Param("tableName") String tableName,
            @Param("clientId") String clientId
    );

    @Select("SELECT * FROM sync_record " +
            "WHERE user_id = #{userId} " +
            "AND (#{keyFingerprint} = '' OR key_fingerprint = #{keyFingerprint} OR key_fingerprint = '') " +
            "AND server_updated_at > #{since} AND server_updated_at <= #{until} " +
            "ORDER BY server_updated_at ASC, id ASC LIMIT #{limit} OFFSET #{offset}")
    List<SyncRecord> selectByUserBetweenAndKey(
            @Param("userId") String userId,
            @Param("keyFingerprint") String keyFingerprint,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    @Select("SELECT COUNT(*) FROM sync_record " +
            "WHERE user_id = #{userId} AND deleted_at IS NULL " +
            "AND (key_fingerprint = #{keyFingerprint} OR key_fingerprint = '')")
    int countByUserAndKey(
            @Param("userId") String userId,
            @Param("keyFingerprint") String keyFingerprint
    );

    @Select("SELECT COUNT(*) FROM sync_record " +
            "WHERE user_id = #{userId} AND deleted_at IS NULL " +
            "AND key_fingerprint <> '' AND key_fingerprint <> #{keyFingerprint}")
    int countByUserAndOtherKey(
            @Param("userId") String userId,
            @Param("keyFingerprint") String keyFingerprint
    );
}
