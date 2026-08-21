package online.yudream.base.domain.platform.milky.sandbox;

import java.util.List;
import java.util.Optional;

public interface QqSandboxCaseRepo {
    List<QqSandboxCase> findAll();

    Optional<QqSandboxCase> findById(String id);

    void save(QqSandboxCase sandboxCase);

    void delete(String id);
}
