package online.yudream.base.infra.platform.milky.mapper;

import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.infra.platform.milky.dataobj.MilkyConnectionDO;
import online.yudream.base.infra.platform.milky.service.MilkyCredentialCipher;

public final class MilkyInfraMapper {
    private MilkyInfraMapper() { }

    public static MilkyConnectionDO toDataObj(MilkyConnection source, MilkyCredentialCipher cipher) {
        if (source == null) {
            return null;
        }
        MilkyConnectionDO target = new MilkyConnectionDO();
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        target.setName(source.getName());
        target.setProtocol(source.protocolCode());
        target.setBaseUrl(source.getBaseUrl());
        target.setEncryptedToken(encryptOptional(cipher, source.getToken(), source.getId()));
        target.setAppId(source.getAppId());
        target.setEncryptedAppSecret(encryptOptional(cipher, source.getAppSecret(), source.getId()));
        target.setSandbox(source.isSandbox());
        target.setIntents(source.getIntents());
        target.setEnabled(source.isEnabled());
        target.setCommandMenuImageMode(source.getCommandMenuImageMode());
        target.setCommandMenuPublicBaseUrl(source.getCommandMenuPublicBaseUrl());
        return target;
    }

    public static MilkyConnection toDomain(MilkyConnectionDO source, MilkyCredentialCipher cipher) {
        if (source == null) {
            return null;
        }
        return MilkyConnection.builder()
                .id(source.getId())
                .version(source.getVersion())
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .name(source.getName())
                .protocol(MilkyConnectionProtocol.from(source.getProtocol()))
                .baseUrl(source.getBaseUrl())
                .token(decryptOptional(cipher, source.getEncryptedToken(), source.getId()))
                .appId(source.getAppId())
                .appSecret(decryptOptional(cipher, source.getEncryptedAppSecret(), source.getId()))
                .sandbox(source.isSandbox())
                .intents(source.getIntents())
                .enabled(source.isEnabled())
                .commandMenuImageMode(source.getCommandMenuImageMode())
                .commandMenuPublicBaseUrl(source.getCommandMenuPublicBaseUrl())
                .build();
    }

    private static String encryptOptional(MilkyCredentialCipher cipher, String value, Long connectionId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return cipher.encrypt(value, connectionId);
    }

    private static String decryptOptional(MilkyCredentialCipher cipher, String value, Long connectionId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return cipher.decrypt(value, connectionId);
    }
}
