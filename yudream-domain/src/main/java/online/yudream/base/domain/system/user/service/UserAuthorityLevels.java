package online.yudream.base.domain.system.user.service;

import online.yudream.base.domain.system.user.aggregate.Role;

import java.util.List;

/**
 * 用户权限等级换算：操作者与目标的角色权重上限比较共用一处，
 * 供「伪装」「删除」等要求操作者等级严格高于目标的守卫使用。
 */
public final class UserAuthorityLevels {

    private UserAuthorityLevels() {
    }

    /** 一组角色的最高权重；无角色、等级为空均为 0。 */
    public static int highestWeight(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return 0;
        }
        return roles.stream()
                .mapToInt(role -> role.getLevel() == null ? 0 : role.getLevel().getWeight())
                .max()
                .orElse(0);
    }
}
