package io.healthresetplan.modules.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.healthresetplan.modules.membership.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
}
