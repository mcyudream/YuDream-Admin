package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.system.user.aggregate.Role;

import java.util.List;
import java.util.Optional;

/** 按授权数组快照原子更新权限，保留角色其他并发修改。 */
public interface RolePermissionChangesRepo {
    Optional<Role> compareAndSet(Long roleId, List<String> expectedPermissions, List<String> permissions);
}
