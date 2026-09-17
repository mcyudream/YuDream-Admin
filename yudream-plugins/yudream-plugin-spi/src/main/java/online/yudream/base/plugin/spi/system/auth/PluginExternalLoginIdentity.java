package online.yudream.base.plugin.spi.system.auth;

/**
 * 外部认证方返回的用户身份。socialUid 是该提供方 + 类型下稳定唯一的用户标识
 * （如 OIDC sub、CAS 用户名），宿主据此建立外部账号绑定；nickname/avatarUrl/gender/location
 * 仅作展示与同步用途，均可为空。
 *
 * <p>2.29.0 起 authenticatedUserId 可声明已由插件服务端证明的本站用户（字符串 ID）。
 * 仅允许来自本次认证后新建的用户，或经过本站凭据验证的用户；不得从浏览器参数、邮箱匹配
 * 或昵称推导。宿主在核销 state 后检查账号状态和已有绑定冲突，再建立绑定、签发登录态。
 * 未证明本站账号归属时必须为 null，宿主继续原有的 BIND_REQUIRED 流程。
 */
public record PluginExternalLoginIdentity(String socialUid, String nickname, String avatarUrl,
                                          String gender, String location, String authenticatedUserId) {
    /** 保留旧构造器，使已有插件无需重新编译。 */
    public PluginExternalLoginIdentity(String socialUid, String nickname, String avatarUrl,
                                       String gender, String location) {
        this(socialUid, nickname, avatarUrl, gender, location, null);
    }
}
