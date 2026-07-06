package online.yudream.base.plugin.alipay.domain.valobj;

public record AlipayConfig(
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

    public static AlipayConfig defaults() {
        return new AlipayConfig(null, null, null, "https://openapi.alipay.com/gateway.do",
                null, null, "RSA2", "UTF-8", false);
    }

    public AlipayConfig normalized() {
        AlipayConfig defaults = defaults();
        return new AlipayConfig(
                trimToNull(appId),
                trimToNull(privateKey),
                trimToNull(alipayPublicKey),
                hasText(gatewayUrl) ? gatewayUrl.trim() : defaults.gatewayUrl,
                trimToNull(notifyUrl),
                trimToNull(returnUrl),
                hasText(signType) ? signType.trim().toUpperCase() : defaults.signType,
                hasText(charset) ? charset.trim() : defaults.charset,
                enabled
        );
    }

    public void ensureUsable() {
        if (!enabled) {
            throw new IllegalArgumentException("支付宝收款未启用");
        }
        require(appId, "支付宝 AppId 不能为空");
        require(privateKey, "应用私钥不能为空");
        require(alipayPublicKey, "支付宝公钥不能为空");
    }

    public AlipayConfig withoutSecrets() {
        return new AlipayConfig(appId, mask(privateKey), mask(alipayPublicKey), gatewayUrl, notifyUrl, returnUrl,
                signType, charset, enabled);
    }

    private static String mask(String value) {
        if (!hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.length() <= 12) {
            return "******";
        }
        return text.substring(0, 6) + "******" + text.substring(text.length() - 6);
    }

    private static void require(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
