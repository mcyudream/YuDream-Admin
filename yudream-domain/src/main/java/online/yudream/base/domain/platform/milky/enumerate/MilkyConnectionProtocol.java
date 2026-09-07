package online.yudream.base.domain.platform.milky.enumerate;

import online.yudream.base.domain.common.exception.BizException;

/**
 * 消息连接出站协议。上层发送/收事件走同一套端口，协议差异只在传输适配器消化。
 */
public enum MilkyConnectionProtocol {
    MILKY("milky"),
    OFFICIAL("official");

    public static final String OFFICIAL_API = "https://api.bot.qq.com";
    public static final String OFFICIAL_SANDBOX_API = "https://sandbox.api.bot.qq.com";
    public static final String OFFICIAL_TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
    public static final String OFFICIAL_GATEWAY = "wss://api.bot.qq.com/websocket";
    public static final String OFFICIAL_SANDBOX_GATEWAY = "wss://sandbox.api.bot.qq.com/websocket";
    /** 群/C2C 事件 + 互动按钮 + 公域频道消息。 */
    public static final int DEFAULT_OFFICIAL_INTENTS = (1 << 25) | (1 << 26) | (1 << 30);

    private final String code;

    MilkyConnectionProtocol(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean official() {
        return this == OFFICIAL;
    }

    public static MilkyConnectionProtocol from(String value) {
        if (value == null || value.isBlank()) {
            return MILKY;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "milky" -> MILKY;
            case "official", "qqbot", "openapi", "qq_official" -> OFFICIAL;
            default -> throw new BizException("不支持的消息协议: " + value);
        };
    }
}
