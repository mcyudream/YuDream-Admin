package online.yudream.base.plugin.alipay.interfaces.request;

public record AlipayConfigSaveRequest(
        String appId,
        String privateKey,
        String alipayPublicKey,
        String gatewayUrl,
        String notifyUrl,
        String returnUrl,
        String signType,
        String charset,
        Boolean enabled
) {
}
