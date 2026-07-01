package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;

import java.util.Optional;

public interface RoleRepo {

    Role save(Role role);

    Optional<Role> findById(Long id);

    Optional<Role> findByCode(String code);

    Optional<Role> findBySystemType(SystemRoleType systemType);
}
