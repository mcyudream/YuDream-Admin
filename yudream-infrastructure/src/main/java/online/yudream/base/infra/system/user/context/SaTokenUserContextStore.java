package online.yudream.base.infra.system.user.context;

import cn.dev33.satoken.stp.StpUtil;
import online.yudream.base.domain.system.user.service.UserContextStore;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token Session 的用户上下文存储实现。
 */
@Component
public class SaTokenUserContextStore implements UserContextStore {

    private static final String CURRENT_DEPT_KEY = "currentDeptId";
    private static final String CURRENT_ROLE_KEY = "currentRoleId";

    @Override
    public void setCurrentDept(Long userId, Long deptId) {
        StpUtil.getTokenSession().set(CURRENT_DEPT_KEY, deptId);
    }

    @Override
    public void setCurrentRole(Long userId, Long roleId) {
        StpUtil.getTokenSession().set(CURRENT_ROLE_KEY, roleId);
    }

    @Override
    public Long getCurrentDeptId(Long userId) {
        Object value = StpUtil.getTokenSession().get(CURRENT_DEPT_KEY);
        return value == null ? null : ((Number) value).longValue();
    }

    @Override
    public Long getCurrentRoleId(Long userId) {
        Object value = StpUtil.getTokenSession().get(CURRENT_ROLE_KEY);
        return value == null ? null : ((Number) value).longValue();
    }

    @Override
    public void clear(Long userId) {
        StpUtil.getTokenSession().delete(CURRENT_DEPT_KEY);
        StpUtil.getTokenSession().delete(CURRENT_ROLE_KEY);
    }
}
