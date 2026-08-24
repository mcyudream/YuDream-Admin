package online.yudream.base.plugin.spi.system.graph;

public record PluginGraphError(PluginGraphErrorCode code, String message) {

    public PluginGraphError {
        code = code == null ? PluginGraphErrorCode.QUERY_FAILED : code;
        message = message == null ? "" : message;
    }
}
