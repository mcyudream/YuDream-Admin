package online.yudream.base.domain.system.user.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;

@AllArgsConstructor
@Getter
public class RoleID {
    private final Long value;

    public static RoleID of(Long value){
        return new RoleID(value);
    }

    public static RoleID fromTrusted(Long value){
        return new RoleID(value);
    }

    public static Long genSystemRoleID(SystemRoleType role){
        return (long) role.getCode().hashCode();
    }
}
