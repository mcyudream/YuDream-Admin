package online.yudream.base.domain.system.user.enumerate;

import online.yudream.base.domain.common.exception.BizException;

/**
 * 消息协议侧身份类型。Milky 是 QQ 号；官方 OpenAPI 只有 openid，群与私聊不是同一套。
 */
public enum MessagingIdentityType {
    QQ("qq"),
    USER_OPENID("user_openid"),
    MEMBER_OPENID("member_openid");

    private final String code;

    MessagingIdentityType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean milkyQq() {
        return this == QQ;
    }

    public boolean officialUserOpenid() {
        return this == USER_OPENID;
    }

    public boolean officialMemberOpenid() {
        return this == MEMBER_OPENID;
    }

    public static MessagingIdentityType from(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException("消息身份类型不能为空");
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "qq" -> QQ;
            case "user_openid", "useropenid", "openid" -> USER_OPENID;
            case "member_openid", "memberopenid" -> MEMBER_OPENID;
            default -> throw new BizException("不支持的消息身份类型: " + value);
        };
    }
}
