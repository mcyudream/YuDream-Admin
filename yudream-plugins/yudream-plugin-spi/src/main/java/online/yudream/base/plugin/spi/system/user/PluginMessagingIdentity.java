package online.yudream.base.plugin.spi.system.user;

/**
 * 系统用户在 QQ 消息协议上的身份快照。
 *
 * <p>{@code protocol} 为 {@code milky} 或 {@code official}。身份类型：
 * {@code qq}（Milky QQ 号）、{@code user_openid}（官方私聊）、{@code member_openid}（官方群成员）。
 * 官方 OpenAPI 不提供真实 QQ 号，群与私聊 openid 不是同一身份。
 */
public record PluginMessagingIdentity(
        String protocol,
        String identityType,
        String identity,
        String appId,
        String groupOpenid,
        String connectionId
) {
}
