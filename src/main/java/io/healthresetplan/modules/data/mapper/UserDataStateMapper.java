package io.healthresetplan.modules.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.healthresetplan.modules.data.entity.UserDataState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserDataStateMapper extends BaseMapper<UserDataState> {
    @Update("""
            UPDATE user_data_state
            SET payload_cipher = #{payloadCipher},
                payload_nonce = #{payloadNonce},
                key_version = #{keyVersion},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("userId") String userId,
            @Param("payloadCipher") String payloadCipher,
            @Param("payloadNonce") String payloadNonce,
            @Param("keyVersion") int keyVersion,
            @Param("expectedVersion") long expectedVersion);
}
