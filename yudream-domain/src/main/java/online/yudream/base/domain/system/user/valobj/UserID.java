package online.yudream.base.domain.system.user.valobj;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class UserID {
    private final Long value;

    public static UserID of(Long value){
        return new UserID(value);
    }

    public static UserID fromTrusted(Long value){
        return new UserID(value);
    }
}
