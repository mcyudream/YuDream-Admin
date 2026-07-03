package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;

import java.util.List;
import java.util.Optional;

public interface RoleRepo {

    Role save(Role role);

    Optional<Role> findById(Long id);

    Optional<Role> findByCode(String code);

    Optional<Role> findBySystemType(SystemRoleType systemType);

    List<Role> findAll();

    List<Role> findByIds(List<Long> ids);

    PageResult<Role> page(String keyword, Long deptId, RoleStatus status, int page, int size);

    boolean existsByCodeExcludeId(String code, Long excludeId);
}
