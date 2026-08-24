package online.yudream.base.plugin.spi.system.graph;

/** Safe result of resolving the calling plugin's unique active graph-table binding. */
public record PluginGraphBindingStatus(boolean available, PluginGraphTable table, PluginGraphError error) {
    public PluginGraphBindingStatus {
        if (available) {
            error = null;
        } else {
            table = null;
            error = error == null
                    ? new PluginGraphError(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, "Graph capability is unavailable")
                    : error;
        }
    }

    public static PluginGraphBindingStatus available(PluginGraphTable table) {
        return new PluginGraphBindingStatus(true, table, null);
    }

    public static PluginGraphBindingStatus unavailable(PluginGraphErrorCode code, String message) {
        return new PluginGraphBindingStatus(false, null, new PluginGraphError(code, message));
    }
}
