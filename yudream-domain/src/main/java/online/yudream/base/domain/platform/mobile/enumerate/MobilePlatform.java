package online.yudream.base.domain.platform.mobile.enumerate;

import online.yudream.base.domain.common.exception.BizException;

/**
 * 移动 App 平台类型。插件 mobile 声明与设备注册共用同一取值域（android/ios）。
 */
public enum MobilePlatform {

    ANDROID,
    IOS;

    public static final String ANDROID_TOKEN = "android";
    public static final String IOS_TOKEN = "ios";

    /** 协议层小写标识，用于 plugin.yml 声明、manifest 请求参数与 JSON 输出。 */
    public String token() {
        return this == ANDROID ? ANDROID_TOKEN : IOS_TOKEN;
    }

    public static MobilePlatform fromToken(String token) {
        if (ANDROID_TOKEN.equalsIgnoreCase(token)) {
            return ANDROID;
        }
        if (IOS_TOKEN.equalsIgnoreCase(token)) {
            return IOS;
        }
        throw new BizException("移动平台仅支持 android/ios：" + token);
    }

    public static boolean isLegalToken(String token) {
        return ANDROID_TOKEN.equalsIgnoreCase(token) || IOS_TOKEN.equalsIgnoreCase(token);
    }
}
