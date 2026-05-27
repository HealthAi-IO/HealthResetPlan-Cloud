package io.healthresetplan.modules.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.healthresetplan.modules.membership.entity.UserSubscription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserSubscriptionMapper extends BaseMapper<UserSubscription> {
}
