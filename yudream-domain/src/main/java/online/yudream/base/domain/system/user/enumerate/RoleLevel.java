package online.yudream.base.domain.system.user.enumerate;

import lombok.Getter;

@Getter
public enum RoleLevel {
    SUPER_ADMIN(100, "超级管理员"),
    ADMIN(80, "管理员"),
    USER(50, "用户"),
    GUEST(20, "访客");

    private final int weight;
    private final String label;

    RoleLevel(int weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    /**
     * 当前角色能否管理目标角色
     * 规则：必须 weight > target.weight，且管理员不能操作超管
     */
    public boolean canManage(RoleLevel target) {
        if (this == target) return true; // 可管理同级（编辑自己等）
        return this.weight > target.weight;
    }

    public boolean isManagement() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}