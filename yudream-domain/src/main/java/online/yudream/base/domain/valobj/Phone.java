package online.yudream.base.domain.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.regex.Pattern;

@AllArgsConstructor
@Getter
public class Phone {
    private final String value;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    /**
     * 工厂方法：校验后创建
     */
    public static Phone of(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        String cleaned = phone.trim();
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("手机号格式不正确: " + mask(phone));
        }
        return new Phone(cleaned);
    }

    /**
     * 从已校验的可靠数据源重建（如数据库加载）
     */
    public static Phone fromTrusted(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        return new Phone(phone.trim());
    }

    public String getValue() {
        return value;
    }

    /**
     * 脱敏展示：138****1234
     */
    public String mask() {
        if (value.length() != 11) {
            return mask(value);
        }
        return value.substring(0, 3) + "****" + value.substring(7);
    }

    private static String mask(String raw) {
        if (raw == null || raw.length() <= 4) {
            return "****";
        }
        return raw.substring(0, 2) + "****" + raw.substring(raw.length() - 2);
    }
}
