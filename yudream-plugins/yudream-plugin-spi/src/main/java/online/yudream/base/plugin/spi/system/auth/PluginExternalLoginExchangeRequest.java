package online.yudream.base.plugin.spi.system.auth;

/**
 * 回调票据交换请求。ticket 是授权方回调带回的凭据（OIDC/OAuth2 的 code，CAS 的 ST ticket），
 * state 与授权阶段签发的一致；platformType 为 state 票据中登记的类型。
 */
public record PluginExternalLoginExchangeRequest(String platformType, String ticket, String state) {
}
