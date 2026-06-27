package online.yudream.base.domain.valobj;

import cn.hutool.core.util.ReUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Email {

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";

    private final String value;

    public static Email of(String email) {
        if (!ReUtil.isMatch(EMAIL_REGEX, email)) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return new Email(email);
    }

    public static Email fromTrusted(String email) {
        return Email.of(email);
    }

    @Override
    public String toString() {
        return value;
    }
}
