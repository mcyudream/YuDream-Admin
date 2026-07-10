package online.yudream.base.domain.platform.satori.service;

/** Records sanitized platform operation diagnostics without credentials or message payloads. */
public interface SatoriOperationLogger {
    void info(Long connectionId, String category, String action, String detail);
    void warn(Long connectionId, String category, String action, String detail);
    void error(Long connectionId, String category, String action, String detail);
}
