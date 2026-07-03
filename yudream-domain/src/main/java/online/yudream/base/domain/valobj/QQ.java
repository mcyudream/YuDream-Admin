package online.yudream.base.domain.valobj.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class QQ {
    private String value;

    public static QQ of(String qq) {
        return new QQ(qq);
    }

    public static QQ fromTrusted(String qq) {
        return new QQ(qq);
    }

}
