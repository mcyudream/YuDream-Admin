package online.yudream.base.domain.system.user.enumerate;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum SystemRoleType {
    SUPER_ADMIN("super_admin", "超级管理员", RoleLevel.SUPER_ADMIN),
    ADMIN("admin", "管理员", RoleLevel.ADMIN),
    USER("user", "普通用户", RoleLevel.USER),
    GUEST("guest", "访客", RoleLevel.GUEST);

    private final String code;
    private final String name;
    private final RoleLevel level;

    SystemRoleType(String code, String name, RoleLevel level) {
        this.code = code;
        this.name = name;
        this.level = level;
    }

    public static SystemRoleType fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}