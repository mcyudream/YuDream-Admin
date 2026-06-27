package online.yudream.base.domain.system.user.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
}
