package io.healthresetplan.modules.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.healthresetplan.modules.sync.entity.HealthIndicator;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface HealthIndicatorMapper extends BaseMapper<HealthIndicator> {

    @Select("SELECT * FROM health_indicator " +
            "WHERE user_id = #{userId} AND server_updated_at > #{since} AND deleted_at IS NULL " +
            "ORDER BY server_updated_at ASC LIMIT #{limit}")
    List<HealthIndicator> selectByUserSince(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit
    );
}
