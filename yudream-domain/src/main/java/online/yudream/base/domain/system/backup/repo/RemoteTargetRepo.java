package online.yudream.base.domain.system.backup.repo;

import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;

import java.util.List;
import java.util.Optional;

public interface RemoteTargetRepo {

    RemoteTarget save(RemoteTarget target);

    Optional<RemoteTarget> findById(Long id);

    Optional<RemoteTarget> findByCode(String code);

    List<RemoteTarget> findAll();

    List<RemoteTarget> findByEnabled(boolean enabled);

    void deleteById(Long id);
}
