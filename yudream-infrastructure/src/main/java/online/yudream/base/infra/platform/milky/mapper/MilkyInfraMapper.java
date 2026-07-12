package online.yudream.base.infra.platform.milky.mapper;

import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.infra.platform.milky.dataobj.MilkyConnectionDO;
import online.yudream.base.infra.platform.milky.service.MilkyCredentialCipher;

public final class MilkyInfraMapper {
    private MilkyInfraMapper() { }
    public static MilkyConnectionDO toDataObj(MilkyConnection source, MilkyCredentialCipher cipher) {
        if (source == null) return null;
        MilkyConnectionDO target = new MilkyConnectionDO();
        target.setId(source.getId()); target.setVersion(source.getVersion()); target.setCreateTime(source.getCreateTime()); target.setUpdateTime(source.getUpdateTime());
        target.setName(source.getName()); target.setBaseUrl(source.getBaseUrl()); target.setEncryptedToken(cipher.encrypt(source.getToken())); target.setEnabled(source.isEnabled()); target.setCommandMenuImageMode(source.getCommandMenuImageMode()); target.setCommandMenuPublicBaseUrl(source.getCommandMenuPublicBaseUrl());
        return target;
    }
    public static MilkyConnection toDomain(MilkyConnectionDO source, MilkyCredentialCipher cipher) {
        if (source == null) return null;
        return MilkyConnection.builder().id(source.getId()).version(source.getVersion()).createTime(source.getCreateTime()).updateTime(source.getUpdateTime())
                .name(source.getName()).baseUrl(source.getBaseUrl()).token(cipher.decrypt(source.getEncryptedToken())).enabled(source.isEnabled()).commandMenuImageMode(source.getCommandMenuImageMode()).commandMenuPublicBaseUrl(source.getCommandMenuPublicBaseUrl()).build();
    }
}
