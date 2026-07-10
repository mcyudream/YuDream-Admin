package online.yudream.base.domain.platform.satori.service;

/** Lifecycle port for the per-connection Satori event transport. */
public interface SatoriEventGateway {
    void connect(Long connectionId);
    void close(Long connectionId);
    void closeAll();
}
