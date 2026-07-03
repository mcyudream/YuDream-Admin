package online.yudream.base.infra.system.security.mapper;

import online.yudream.base.domain.system.security.aggregate.ApiKeyCredential;
import online.yudream.base.domain.system.security.aggregate.RefreshTokenCredential;
import online.yudream.base.domain.system.security.valobj.ApiKeySecret;
import online.yudream.base.domain.system.security.valobj.PermissionScope;
import online.yudream.base.infra.system.security.dataobj.ApiKeyCredentialDO;
import online.yudream.base.infra.system.security.dataobj.RefreshTokenCredentialDO;

public class ApiKeyCredentialInfraMapper {

    public static RefreshTokenCredentialDO toDataObj(RefreshTokenCredential credential) {
        if (credential == null) {
            return null;
        }
        RefreshTokenCredentialDO dataObj = new RefreshTokenCredentialDO();
        dataObj.setId(credential.getId());
        dataObj.setTokenHash(credential.getTokenHash());
        dataObj.setUserId(credential.getUserId());
        dataObj.setExpireTime(credential.getExpireTime());
        dataObj.setStatus(credential.getStatus());
        dataObj.setUsedTime(credential.getUsedTime());
        dataObj.setVersion(credential.getVersion());
        dataObj.setCreateTime(credential.getCreateTime());
        dataObj.setUpdateTime(credential.getUpdateTime());
        return dataObj;
    }

    public static RefreshTokenCredential toDomain(RefreshTokenCredentialDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return RefreshTokenCredential.builder()
                .id(dataObj.getId())
                .tokenHash(dataObj.getTokenHash())
                .userId(dataObj.getUserId())
                .expireTime(dataObj.getExpireTime())
                .status(dataObj.getStatus())
                .usedTime(dataObj.getUsedTime())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    public static ApiKeyCredentialDO toDataObj(ApiKeyCredential credential) {
        if (credential == null) {
            return null;
        }
        ApiKeyCredentialDO dataObj = new ApiKeyCredentialDO();
        dataObj.setId(credential.getId());
        dataObj.setName(credential.getName());
        if (credential.getSecret() != null) {
            dataObj.setKeyPrefix(credential.getSecret().prefix());
            dataObj.setSecretHash(credential.getSecret().secretHash());
            dataObj.setMaskedValue(credential.getSecret().maskedValue());
        }
        dataObj.setCreatorUserId(credential.getCreatorUserId());
        dataObj.setPermissions(credential.getPermissionScope() == null ? null : credential.getPermissionScope().permissions());
        dataObj.setExpireTime(credential.getExpireTime());
        dataObj.setStatus(credential.getStatus());
        dataObj.setLastUsedTime(credential.getLastUsedTime());
        dataObj.setVersion(credential.getVersion());
        dataObj.setCreateTime(credential.getCreateTime());
        dataObj.setUpdateTime(credential.getUpdateTime());
        return dataObj;
    }

    public static ApiKeyCredential toDomain(ApiKeyCredentialDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return ApiKeyCredential.builder()
                .id(dataObj.getId())
                .name(dataObj.getName())
                .secret(new ApiKeySecret(dataObj.getKeyPrefix(), dataObj.getSecretHash(), dataObj.getMaskedValue()))
                .creatorUserId(dataObj.getCreatorUserId())
                .permissionScope(new PermissionScope(dataObj.getPermissions()))
                .expireTime(dataObj.getExpireTime())
                .status(dataObj.getStatus())
                .lastUsedTime(dataObj.getLastUsedTime())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
