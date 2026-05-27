package io.healthresetplan.modules.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.healthresetplan.modules.membership.entity.MembershipPlan;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MembershipPlanMapper extends BaseMapper<MembershipPlan> {
}
