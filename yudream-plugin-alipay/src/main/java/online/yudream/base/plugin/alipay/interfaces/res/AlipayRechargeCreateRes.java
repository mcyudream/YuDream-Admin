package online.yudream.base.plugin.alipay.interfaces.res;

public record AlipayRechargeCreateRes(
        AlipayRechargeOrderRes order,
        String payPayload
) {
}
