package online.yudream.base.domain.system.user.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PermissionID {
    private final Long value;

    public static PermissionID of(Long value){
        return new PermissionID(value);
    }

    public static PermissionID fromTrusted(Long value){
        return new PermissionID(value);
    }
}
