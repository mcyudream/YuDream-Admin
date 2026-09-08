package online.yudream.base.domain.system.user.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;
import online.yudream.base.domain.system.user.service.MessagingIdentityClassifier;

/**
 * 系统用户在 QQ 消息协议上的身份。一条用户可积累多条身份；同一作用域身份只属于一个用户。
 * {@code User.qq} 只镜像 Milky 数字 QQ 号，官方 openid 不得写入该字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MessagingIdentity extends BaseDomain {

    private Long userId;
    private MilkyConnectionProtocol protocol;
    private Long connectionId;
    private String appId;
    private MessagingIdentityType identityType;
    private String identity;
    private String groupOpenid;

    public static MessagingIdentity bind(Long userId, MilkyConnectionProtocol protocol, Long connectionId, String appId,
                                         MessagingIdentityType identityType, String identity, String groupOpenid) {
        if (userId == null) {
            throw new BizException("用户不能为空");
        }
        if (protocol == null) {
            throw new BizException("消息协议不能为空");
        }
        if (identityType == null) {
            throw new BizException("消息身份类型不能为空");
        }
        String normalizedIdentity = required(identity, "消息身份不能为空");
        String normalizedAppId = blank(appId) ? null : appId.trim();
        String normalizedGroup = blank(groupOpenid) ? null : groupOpenid.trim();
        if (identityType.milkyQq()) {
            if (protocol != MilkyConnectionProtocol.MILKY) {
                throw new BizException("QQ 号只能绑定到 Milky 协议");
            }
            if (!MessagingIdentityClassifier.milkyQqNumber(normalizedIdentity)) {
                throw new BizException("QQ 号格式无效");
            }
            normalizedAppId = null;
            normalizedGroup = null;
        } else if (protocol != MilkyConnectionProtocol.OFFICIAL) {
            throw new BizException("openid 只能绑定到官方 QQ 协议");
        } else if (identityType.officialMemberOpenid() && blank(normalizedGroup)) {
            throw new BizException("群成员 openid 必须带上群 openid");
        } else if (identityType.officialUserOpenid()) {
            normalizedGroup = null;
        }
        return MessagingIdentity.builder()
                .userId(userId)
                .protocol(protocol)
                .connectionId(connectionId)
                .appId(normalizedAppId)
                .identityType(identityType)
                .identity(normalizedIdentity)
                .groupOpenid(normalizedGroup)
                .build();
    }

    public boolean matchesScope(MilkyConnectionProtocol protocol, MessagingIdentityType identityType,
                                String appId, String groupOpenid, String identity) {
        if (this.protocol != protocol || this.identityType != identityType) {
            return false;
        }
        if (!this.identity.equals(identity == null ? null : identity.trim())) {
            return false;
        }
        if (identityType.milkyQq()) {
            return true;
        }
        if (!sameOptional(this.appId, appId)) {
            return false;
        }
        return !identityType.officialMemberOpenid() || sameOptional(this.groupOpenid, groupOpenid);
    }

    public boolean ownedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    private static String required(String value, String message) {
        if (blank(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean sameOptional(String left, String right) {
        String normalizedLeft = blank(left) ? null : left.trim();
        String normalizedRight = blank(right) ? null : right.trim();
        if (normalizedLeft == null || normalizedRight == null) {
            return normalizedLeft == null && normalizedRight == null;
        }
        return normalizedLeft.equals(normalizedRight);
    }
}
