package online.yudream.base.interfaces.platform.mobile.res;

import java.io.Serial;
import java.io.Serializable;

/**
 * 移动端主题激活响应：域主题色（站点主题主色），null 表示无可用主色。
 */
public record MobileThemeActiveRes(String themeCode, String primaryColor) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
