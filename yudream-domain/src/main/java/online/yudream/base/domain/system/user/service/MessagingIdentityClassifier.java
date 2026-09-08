package online.yudream.base.domain.system.user.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;

import java.util.regex.Pattern;

/**
 * 把协议事件或历史 {@code User.qq} 值分类成消息身份作用域。
 * 官方 OpenAPI 不提供 QQ 号，群 {@code member_openid} 与私聊 {@code user_openid} 不是同一身份。
 */
public final class MessagingIdentityClassifier {

    public static final Pattern MILKY_QQ = Pattern.compile("^\\d{5,12}$");

    private MessagingIdentityClassifier() {
    }

    public static boolean milkyQqNumber(String value) {
        return value != null && MILKY_QQ.matcher(value.trim()).matches();
    }

    public static Classification classifyStoredQq(String qq) {
        if (qq == null || qq.isBlank()) {
            throw new BizException("QQ 不能为空");
        }
        String identity = qq.trim();
        if (milkyQqNumber(identity)) {
            return new Classification(MilkyConnectionProtocol.MILKY, MessagingIdentityType.QQ, identity, null, null);
        }
        return new Classification(MilkyConnectionProtocol.OFFICIAL, MessagingIdentityType.USER_OPENID, identity, null, null);
    }

    public static Classification classifyEvent(MilkyConnectionProtocol protocol, String scene, String identity, String groupOpenid, String appId) {
        if (identity == null || identity.isBlank()) {
            throw new BizException("消息身份不能为空");
        }
        MilkyConnectionProtocol kind = protocol == null ? MilkyConnectionProtocol.MILKY : protocol;
        String normalizedIdentity = identity.trim();
        String normalizedAppId = blank(appId) ? null : appId.trim();
        String normalizedGroup = blank(groupOpenid) ? null : groupOpenid.trim();
        if (!kind.official()) {
            return new Classification(MilkyConnectionProtocol.MILKY, MessagingIdentityType.QQ, normalizedIdentity, null, null);
        }
        if (groupScene(scene)) {
            if (blank(normalizedGroup)) {
                throw new BizException("官方群消息缺少群 openid");
            }
            return new Classification(MilkyConnectionProtocol.OFFICIAL, MessagingIdentityType.MEMBER_OPENID,
                    normalizedIdentity, normalizedAppId, normalizedGroup);
        }
        return new Classification(MilkyConnectionProtocol.OFFICIAL, MessagingIdentityType.USER_OPENID,
                normalizedIdentity, normalizedAppId, null);
    }

    public static boolean groupScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return false;
        }
        String normalized = scene.trim().toLowerCase();
        return "group".equals(normalized) || "guild".equals(normalized) || "channel".equals(normalized);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Classification(MilkyConnectionProtocol protocol, MessagingIdentityType identityType, String identity,
                                 String appId, String groupOpenid) {
    }
}
