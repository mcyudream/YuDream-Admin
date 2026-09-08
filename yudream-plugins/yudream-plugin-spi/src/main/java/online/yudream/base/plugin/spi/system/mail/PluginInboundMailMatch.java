package online.yudream.base.plugin.spi.system.mail;

/** Sanitized outcome of a host-managed inbound mailbox verification query. */
public record PluginInboundMailMatch(String status, long receivedAt, String message) {
    public static final String MATCHED = "MATCHED";
    public static final String PENDING = "PENDING";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    public static PluginInboundMailMatch matched(long receivedAt) {
        return new PluginInboundMailMatch(MATCHED, receivedAt, null);
    }

    public static PluginInboundMailMatch pending(String message) {
        return new PluginInboundMailMatch(PENDING, 0L, message);
    }

    public static PluginInboundMailMatch unavailable(String message) {
        return new PluginInboundMailMatch(UNAVAILABLE, 0L, message);
    }
}
