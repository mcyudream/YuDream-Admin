package online.yudream.base.infra.platform.satori.mapper;

import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventRecord;
import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;
import online.yudream.base.domain.platform.satori.service.SatoriCredentialCipher;
import online.yudream.base.infra.platform.satori.dataobj.SatoriConnectionDO;
import online.yudream.base.infra.platform.satori.dataobj.SatoriEventRecordDO;
import online.yudream.base.infra.platform.satori.dataobj.SatoriLoginDO;

public final class SatoriInfraMapper {
    private SatoriInfraMapper() { }

    public static SatoriConnectionDO toDataObj(SatoriConnection source, SatoriCredentialCipher cipher) {
        if (source == null) return null;
        SatoriConnectionDO target = new SatoriConnectionDO();
        copyBase(source, target);
        target.setName(source.getName());
        target.setBaseUrl(source.getBaseUrl());
        target.setEncryptedToken(source.getToken() == null ? null : cipher.encrypt(source.getToken()));
        target.setEnabled(source.isEnabled());
        return target;
    }

    public static SatoriConnection toDomain(SatoriConnectionDO source, SatoriCredentialCipher cipher) {
        if (source == null) return null;
        return SatoriConnection.builder().id(source.getId()).version(source.getVersion()).createTime(source.getCreateTime()).updateTime(source.getUpdateTime())
                .name(source.getName()).baseUrl(source.getBaseUrl())
                .token(source.getEncryptedToken() == null ? null : cipher.decrypt(source.getEncryptedToken()))
                .enabled(source.isEnabled()).build();
    }

    public static SatoriLoginDO toDataObj(SatoriLogin source) {
        if (source == null) return null;
        SatoriLoginDO target = new SatoriLoginDO();
        copyBase(source, target);
        target.setConnectionId(source.getConnectionId()); target.setPlatform(source.getPlatform()); target.setUserId(source.getUserId());
        target.setStatus(source.getStatus()); target.setAdapter(source.getAdapter()); target.setFeatures(source.getFeatures());
        return target;
    }

    public static SatoriLogin toDomain(SatoriLoginDO source) {
        if (source == null) return null;
        return SatoriLogin.builder().id(source.getId()).version(source.getVersion()).createTime(source.getCreateTime()).updateTime(source.getUpdateTime())
                .connectionId(source.getConnectionId()).platform(source.getPlatform()).userId(source.getUserId()).status(source.getStatus())
                .adapter(source.getAdapter()).features(source.getFeatures()).build();
    }

    public static SatoriEventRecordDO toDataObj(SatoriEventRecord source) {
        if (source == null) return null;
        SatoriEventRecordDO target = new SatoriEventRecordDO();
        copyBase(source, target);
        target.setConnectionId(source.getConnectionId()); target.setSequence(source.getSequence()); target.setType(source.getType());
        target.setRawData(source.getRawData()); target.setReceivedAt(source.getReceivedAt()); target.setExpireAt(source.getExpireAt());
        return target;
    }

    public static SatoriEventRecord toDomain(SatoriEventRecordDO source) {
        if (source == null) return null;
        return SatoriEventRecord.builder().id(source.getId()).version(source.getVersion()).createTime(source.getCreateTime()).updateTime(source.getUpdateTime())
                .connectionId(source.getConnectionId()).sequence(source.getSequence()).type(source.getType()).rawData(source.getRawData())
                .receivedAt(source.getReceivedAt()).expireAt(source.getExpireAt()).build();
    }

    private static void copyBase(online.yudream.base.domain.common.base.BaseDomain source, online.yudream.base.infra.common.baseobj.BaseDO target) {
        target.setId(source.getId()); target.setVersion(source.getVersion()); target.setCreateTime(source.getCreateTime()); target.setUpdateTime(source.getUpdateTime());
    }
}
