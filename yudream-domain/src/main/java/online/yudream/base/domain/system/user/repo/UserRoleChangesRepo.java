package online.yudream.base.domain.system.user.repo;

import java.util.List;

/** 按角色数组快照原子更新，并复核新分配角色所需的用户部门。 */
public interface UserRoleChangesRepo {
    boolean compareAndSet(Long userId, List<Long> expectedRoleIds, List<Long> roleIds, List<Long> requiredDeptIds);
}
