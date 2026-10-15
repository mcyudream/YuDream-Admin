package online.yudream.base.domain.system.user.enumerate;

import lombok.Getter;
import online.yudream.base.domain.common.exception.BizException;

/**
 * 角色代表色：只允许固定语义键，不接受任意 hex/色值；前端按语义令牌映射渲染。
 */
@Getter
public enum RoleColor {
    INFO("info"),
    SUCCESS("success"),
    WARNING("warning"),
    DANGER("danger"),
    EMPHASIS("emphasis"),
    NEUTRAL("neutral");

    /** 固定键集合正则，供接口层请求校验引用，与枚举同源维护。 */
    public static final String CODES_PATTERN = "info|success|warning|danger|emphasis|neutral";

    private final String code;

    RoleColor(String code) {
        this.code = code;
    }

    /** 空值回落默认色 info；非法值抛领域异常。 */
    public static RoleColor from(String value) {
        if (value == null || value.isBlank()) {
            return INFO;
        }
        String normalized = value.trim().toLowerCase();
        for (RoleColor color : values()) {
            if (color.code.equals(normalized)) {
                return color;
            }
        }
        throw new BizException("不支持的角色代表色: " + value);
    }
}
