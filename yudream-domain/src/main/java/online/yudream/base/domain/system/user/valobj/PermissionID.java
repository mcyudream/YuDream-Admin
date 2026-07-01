package online.yudream.base.domain.system.user.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@AllArgsConstructor
@Getter
public class PermissionID {
    private final String code;

    public static PermissionID of(String value){
        return new PermissionID(value);
    }

    public static PermissionID fromTrusted(String value){
        return new PermissionID(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(code, ((PermissionID) o).code);
    }

}
