package online.yudream.base.plugin.spi.system.mail;

/** Host-managed inbound mailbox matching. Plugins cannot access mailbox credentials or message bodies. */
public interface PluginInboundMailService {

    default boolean enabled() {
        return false;
    }

    default String mailboxId() {
        return "";
    }

    default PluginInboundMailMatch match(PluginInboundMailQuery query) {
        return PluginInboundMailMatch.unavailable("宿主未配置入站邮件核验能力");
    }
}
