package online.yudream.base.domain.platform.milky.sandbox;

import java.util.List;
import java.util.Optional;

public interface QqSandboxSessionRepo {
    void save(QqSandboxSession session);
    Optional<QqSandboxSession> findById(String id);
    Optional<QqSandboxSession> findByConnectionId(String connectionId);
    List<QqSandboxSession> findAll();
    void delete(String id);
}
