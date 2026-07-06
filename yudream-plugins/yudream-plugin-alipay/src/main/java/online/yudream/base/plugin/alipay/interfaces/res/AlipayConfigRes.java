package online.yudream.base.plugin.alipay.interfaces.res;

public record AlipayConfigRes(
        String appId,
        String privateKey,
        String alipayPublicKey,
        String gatewayUrl,
        String notifyUrl,
        String returnUrl,
        String signType,
        String charset,
        boolean enabled
) {
}
