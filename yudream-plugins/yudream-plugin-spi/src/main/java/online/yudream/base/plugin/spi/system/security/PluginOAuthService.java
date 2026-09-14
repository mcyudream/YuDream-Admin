package online.yudream.base.plugin.spi.system.security;

import java.util.Optional;

/**
 * 插件访问宿主 OAuth 授权服务器的登记端口。
 * <p>
 * 启动器等公开客户端应在 {@code onEnable} 调用 {@link #ensurePublicClient(PluginOAuthPublicClientSpec)}，
 * 由宿主幂等创建或更新系统 OAuth 客户端，插件不得手写内部仓储或管理 HTTP。
 * 宿主未提供该能力时全部方法保持空实现。
 */
public interface PluginOAuthService {

    /** 宿主 OAuth 授权服务器当前是否启用。 */
    default boolean enabled() {
        return false;
    }

    default Optional<PluginOAuthClient> findClient(String clientId) {
        return Optional.empty();
    }

    /**
     * 幂等登记公开客户端（auth method NONE、authorization_code + refresh_token）。
     * 已存在同 clientId 时合并回调地址与 scope，并确保为 ACTIVE 公开客户端。
     * 不修改管理员的 OAuth 服务端总开关。
     */
    default Optional<PluginOAuthClient> ensurePublicClient(PluginOAuthPublicClientSpec spec) {
        return Optional.empty();
    }
}
