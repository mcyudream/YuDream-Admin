package online.yudream.base.plugin.spi.system.messaging;

/**
 * 已启用的消息连接。{@code protocol} 区分同一平台下的出站协议，例如 QQ 的 {@code milky} 与 {@code official}。
 * 四参数构造保持对旧插件源码兼容；缺省协议为 null。
 */
public record PluginMessagingConnection(String id, String name, String platform, String userId, String protocol) {

    public PluginMessagingConnection(String id, String name, String platform, String userId) {
        this(id, name, platform, userId, null);
    }
}
