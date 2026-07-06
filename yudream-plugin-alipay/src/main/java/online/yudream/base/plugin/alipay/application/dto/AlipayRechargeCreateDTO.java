package online.yudream.base.plugin.alipay.application.dto;

import online.yudream.base.plugin.alipay.domain.aggregate.AlipayRechargeOrder;

public record AlipayRechargeCreateDTO(
        AlipayRechargeOrder order,
        String payPayload
) {
}
