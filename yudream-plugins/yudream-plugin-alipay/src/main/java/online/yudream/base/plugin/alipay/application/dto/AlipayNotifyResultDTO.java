package online.yudream.base.plugin.alipay.application.dto;

public record AlipayNotifyResultDTO(
        boolean success,
        String message,
        String outTradeNo,
        String tradeNo
) {
}
