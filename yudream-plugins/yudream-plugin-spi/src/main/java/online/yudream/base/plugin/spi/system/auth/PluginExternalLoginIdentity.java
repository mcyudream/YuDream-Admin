package online.yudream.base.plugin.spi.system.auth;

/**
 * 外部认证方返回的用户身份。socialUid 是该提供方 + 类型下稳定唯一的用户标识
 * （如 OIDC sub、CAS 用户名），宿主据此建立外部账号绑定；nickname/avatarUrl/gender/location
 * 仅作展示与同步用途，均可为空。
 */
public record PluginExternalLoginIdentity(String socialUid, String nickname, String avatarUrl,
                                          String gender, String location) {
}
