package online.yudream.base.plugin.spi.system.graph;
/** Stable graph failure categories; never expose drivers, credentials, or physical configuration. */
public enum PluginGraphErrorCode {
    CAPABILITY_UNAVAILABLE,
    TABLE_NOT_FOUND,
    TABLE_DISABLED,
    TABLE_UNAUTHORIZED,
    TABLE_BINDING_NOT_FOUND,
    TABLE_BINDING_AMBIGUOUS,
    INVALID_QUERY,
    QUERY_FORBIDDEN,
    QUERY_FAILED,
    RESULT_LIMIT_REACHED,
    INVALID_PROJECTION,
    PROJECTION_LIMIT_EXCEEDED,
    PROJECTION_FAILED
}
