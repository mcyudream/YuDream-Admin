package online.yudream.base.plugin.spi.system.auth;

/**
 * 授权地址构造请求。platformType 是本次入口对应的提供方类型；state 是宿主签发的
 * 一次性 CSRF 关联票据，回调时会原样带回并用于核销。
 */
public record PluginExternalLoginAuthorizeRequest(String platformType, String state) {
}
