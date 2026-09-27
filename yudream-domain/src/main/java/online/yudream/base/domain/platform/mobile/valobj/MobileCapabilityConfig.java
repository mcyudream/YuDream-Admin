package online.yudream.base.domain.platform.mobile.valobj;

import java.util.Map;

/**
 * mobile-app 能力配置键与读取语义的共享常量。
 * <p>
 * defaultConfig 只播种空配置行，新键必须在读取处回落默认值：
 * {@link #iosEnabled(Map)} 在键缺失或为空时回落 false（iOS 席位默认预留未开启）。
 */
public final class MobileCapabilityConfig {

    /** 平台能力 code：mobile-app（Provider 与应用闸门共用）。 */
    public static final String CAPABILITY_CODE = "mobile-app";

    public static final String KEY_IOS_ENABLED = "iosEnabled";

    private MobileCapabilityConfig() {
    }

    /** iOS 通道开关：仅显式配置 true 才视为开启，缺省/空/非法值一律 false。 */
    public static boolean iosEnabled(Map<String, String> config) {
        if (config == null) {
            return false;
        }
        return "true".equalsIgnoreCase(config.get(KEY_IOS_ENABLED));
    }
}
