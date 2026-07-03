package online.yudream.base.domain.system.user.service;

/**
 * 用户当前会话上下文存储。
 */
public interface UserContextStore {

    void setCurrentDept(Long userId, Long deptId);

    void setCurrentRole(Long userId, Long roleId);

    Long getCurrentDeptId(Long userId);

    Long getCurrentRoleId(Long userId);

    void clear(Long userId);
}
