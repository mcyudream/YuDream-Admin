package online.yudream.base.domain.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.common.service.PasswordEncoder;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Password {
    private String password;
    private String encodedPassword;

    /**
     * 工厂方法：从明文创建（校验 + 加密）
     */
    public static Password of(String rawPassword, PasswordEncoder passwordEncoder) {
        validate(rawPassword);
        return new Password(rawPassword, passwordEncoder.encode(rawPassword));
    }

    /**
     * 从已加密字符串重建（用于持久化还原）
     */
    public static Password fromEncoded(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("加密密码不能为空");
        }
        return new Password(null, encodedPassword);
    }

    /**
     * 密码强度校验
     */
    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        boolean hasLower = false, hasUpper = false, hasDigit = false;
        for (char c : rawPassword.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }

        int categoryCount = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0) + (hasDigit ? 1 : 0);
        if (categoryCount < 2) {
            throw new IllegalArgumentException("密码必须包含小写字母、大写字母、数字中的至少两项");
        }
    }

    /**
     * 校验明文是否匹配（用于登录）
     */
    public boolean matches(String rawPassword, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(rawPassword, this.encodedPassword);
    }
}
