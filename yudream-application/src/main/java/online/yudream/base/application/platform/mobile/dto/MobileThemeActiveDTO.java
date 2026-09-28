package online.yudream.base.application.platform.mobile.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 移动端主题激活信息：激活站点主题编码与其主色（域主题色）。
 * primaryColor 为 null 表示无可用主色（内置主题/解析失败），App 回退内置主题色。
 */
public record MobileThemeActiveDTO(String themeCode, String primaryColor) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
