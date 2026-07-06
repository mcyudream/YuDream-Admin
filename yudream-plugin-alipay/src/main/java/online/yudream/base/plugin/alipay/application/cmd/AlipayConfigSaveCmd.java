package online.yudream.base.plugin.alipay.application.cmd;

public record AlipayConfigSaveCmd(
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
