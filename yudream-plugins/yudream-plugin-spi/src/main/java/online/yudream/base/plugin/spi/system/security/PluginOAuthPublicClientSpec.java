package online.yudream.base.plugin.spi.system.security;

import java.util.List;

/**
 * 插件向宿主登记的公开 OAuth 客户端（auth method NONE，无 client secret）。
 */
public record PluginOAuthPublicClientSpec(
        String clientId,
        String clientName,
        List<String> redirectUris,
        List<String> scopes
) {
    public PluginOAuthPublicClientSpec {
        redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
