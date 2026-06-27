package online.yudream.base.domain.system.user.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class DeptID {
    private final Long value;

    public static DeptID of(Long value){
        return new DeptID(value);
    }

    public static DeptID fromTrusted(Long value){
        return new DeptID(value);
    }
}
