package online.yudream.base.domain.platform.milky.service;

public interface MilkyEventGateway {
    void connect(Long connectionId);
    void close(Long connectionId);
    void closeAll();
}
