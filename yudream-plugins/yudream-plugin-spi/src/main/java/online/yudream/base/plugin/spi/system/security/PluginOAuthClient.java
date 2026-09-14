package online.yudream.base.plugin.spi.system.security;

import java.util.List;

/**
 * 宿主已登记的 OAuth 客户端摘要。
 */
public record PluginOAuthClient(
        String clientId,
        String clientName,
        List<String> redirectUris,
        List<String> scopes,
        boolean active
) {
    public PluginOAuthClient {
        redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
