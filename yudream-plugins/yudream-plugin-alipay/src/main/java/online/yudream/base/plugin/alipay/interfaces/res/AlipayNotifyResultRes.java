package online.yudream.base.plugin.alipay.interfaces.res;

public record AlipayNotifyResultRes(
        boolean success,
        String message,
        String outTradeNo,
        String tradeNo
) {
}
