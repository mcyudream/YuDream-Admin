package online.yudream.base.infra.system.user.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.system.user.aggregate.MessagingIdentity;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;
import online.yudream.base.infra.system.user.dataobj.MessagingIdentityDO;

@NoArgsConstructor
public class MessagingIdentityInfraMapper {

    public static MessagingIdentityDO toDataObj(MessagingIdentity identity) {
        if (identity == null) {
            return null;
        }
        MessagingIdentityDO data = new MessagingIdentityDO();
        data.setId(identity.getId());
        data.setVersion(identity.getVersion());
        data.setCreateTime(identity.getCreateTime());
        data.setUpdateTime(identity.getUpdateTime());
        data.setUserId(identity.getUserId());
        data.setProtocol(identity.getProtocol() == null ? null : identity.getProtocol().code());
        data.setConnectionId(identity.getConnectionId());
        data.setAppId(identity.getAppId());
        data.setIdentityType(identity.getIdentityType() == null ? null : identity.getIdentityType().code());
        data.setIdentity(identity.getIdentity());
        data.setGroupOpenid(identity.getGroupOpenid());
        return data;
    }

    public static MessagingIdentity toDomain(MessagingIdentityDO data) {
        if (data == null) {
            return null;
        }
        return MessagingIdentity.builder()
                .id(data.getId())
                .version(data.getVersion())
                .createTime(data.getCreateTime())
                .updateTime(data.getUpdateTime())
                .userId(data.getUserId())
                .protocol(data.getProtocol() == null ? null : MilkyConnectionProtocol.from(data.getProtocol()))
                .connectionId(data.getConnectionId())
                .appId(data.getAppId())
                .identityType(data.getIdentityType() == null ? null : MessagingIdentityType.from(data.getIdentityType()))
                .identity(data.getIdentity())
                .groupOpenid(data.getGroupOpenid())
                .build();
    }
}
