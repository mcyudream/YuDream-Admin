package online.yudream.base.domain.system.security.valobj;

import java.util.List;

/**
 * OAuth 2.0 访问令牌在请求上下文的鉴权快照。
 * <p>
 * 由 {@link online.yudream.base.domain.system.security.service.OAuthAuthenticationContext}
 * 维护；过滤器解析 Bearer 后写入，请求结束清除。
 *
 * @param tokenId    OAuth 访问令牌数据库主键（{@code OAuthAccessToken.id}），用于日志关联
 * @param userId     令牌所属用户
 * @param clientId   颁发该令牌的客户端 ID
 * @param scopes     令牌持有的 OAuth 授权范围，约定与权限码同形（如 {@code plugin:launcher-adapter:view}）
 */
public record OAuthAuthentication(
        String tokenId,
        Long userId,
        String clientId,
        List<String> scopes
) {

    public boolean hasPermission(String permission) {
        return scopes != null && (scopes.contains("*") || scopes.contains(permission));
    }
}