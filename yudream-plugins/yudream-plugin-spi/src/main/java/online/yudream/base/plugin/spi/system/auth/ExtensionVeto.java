package online.yudream.base.plugin.spi.system.auth;

/**
 * 拦截型扩展点的裁决结果。allowed=true 表示放行；allowed=false 表示否决，
 * reason 会直接作为错误文案返回给终端用户，必须使用正常中文文案。
 */
public record ExtensionVeto(boolean allowed, String reason) {

    private static final ExtensionVeto ALLOW = new ExtensionVeto(true, null);

    public static ExtensionVeto allow() {
        return ALLOW;
    }

    public static ExtensionVeto deny(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("否决原因不能为空");
        }
        return new ExtensionVeto(false, reason.trim());
    }
}
