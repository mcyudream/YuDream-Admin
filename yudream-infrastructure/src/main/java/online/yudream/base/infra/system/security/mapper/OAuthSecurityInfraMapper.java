package online.yudream.base.infra.system.security.mapper;

import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;
import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;
import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;
import online.yudream.base.infra.system.security.dataobj.OAuthClientRegistrationDO;
import online.yudream.base.infra.system.security.dataobj.OAuthProviderRegistrationDO;
import online.yudream.base.infra.system.security.dataobj.PasskeyCredentialDO;

public class OAuthSecurityInfraMapper {

    private OAuthSecurityInfraMapper() {
    }

    public static OAuthClientRegistrationDO toDataObj(OAuthClientRegistration registration) {
        if (registration == null) {
            return null;
        }
        OAuthClientRegistrationDO dataObj = new OAuthClientRegistrationDO();
        dataObj.setId(registration.getId());
        dataObj.setClientId(registration.getClientId());
        dataObj.setClientName(registration.getClientName());
        dataObj.setClientSecretHash(registration.getClientSecretHash());
        dataObj.setAuthMethod(registration.getAuthMethod());
        dataObj.setGrantTypes(registration.getGrantTypes());
        dataObj.setRedirectUris(registration.getRedirectUris());
        dataObj.setScopes(registration.getScopes());
        dataObj.setAccessTokenTtlSeconds(registration.getAccessTokenTtlSeconds());
        dataObj.setRefreshTokenTtlSeconds(registration.getRefreshTokenTtlSeconds());
        dataObj.setStatus(registration.getStatus());
        dataObj.setVersion(registration.getVersion());
        dataObj.setCreateTime(registration.getCreateTime());
        dataObj.setUpdateTime(registration.getUpdateTime());
        return dataObj;
    }

    public static OAuthClientRegistration toDomain(OAuthClientRegistrationDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return OAuthClientRegistration.builder()
                .id(dataObj.getId())
                .clientId(dataObj.getClientId())
                .clientName(dataObj.getClientName())
                .clientSecretHash(dataObj.getClientSecretHash())
                .authMethod(dataObj.getAuthMethod())
                .grantTypes(dataObj.getGrantTypes())
                .redirectUris(dataObj.getRedirectUris())
                .scopes(dataObj.getScopes())
                .accessTokenTtlSeconds(dataObj.getAccessTokenTtlSeconds())
                .refreshTokenTtlSeconds(dataObj.getRefreshTokenTtlSeconds())
                .status(dataObj.getStatus())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    public static OAuthProviderRegistrationDO toDataObj(OAuthProviderRegistration registration) {
        if (registration == null) {
            return null;
        }
        OAuthProviderRegistrationDO dataObj = new OAuthProviderRegistrationDO();
        dataObj.setId(registration.getId());
        dataObj.setCode(registration.getCode());
        dataObj.setName(registration.getName());
        dataObj.setIssuerUri(registration.getIssuerUri());
        dataObj.setAuthorizationUri(registration.getAuthorizationUri());
        dataObj.setTokenUri(registration.getTokenUri());
        dataObj.setUserInfoUri(registration.getUserInfoUri());
        dataObj.setClientId(registration.getClientId());
        dataObj.setClientSecretHash(registration.getClientSecretHash());
        dataObj.setAuthMethod(registration.getAuthMethod());
        dataObj.setScopes(registration.getScopes());
        dataObj.setRedirectUri(registration.getRedirectUri());
        dataObj.setStatus(registration.getStatus());
        dataObj.setVersion(registration.getVersion());
        dataObj.setCreateTime(registration.getCreateTime());
        dataObj.setUpdateTime(registration.getUpdateTime());
        return dataObj;
    }

    public static OAuthProviderRegistration toDomain(OAuthProviderRegistrationDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return OAuthProviderRegistration.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .name(dataObj.getName())
                .issuerUri(dataObj.getIssuerUri())
                .authorizationUri(dataObj.getAuthorizationUri())
                .tokenUri(dataObj.getTokenUri())
                .userInfoUri(dataObj.getUserInfoUri())
                .clientId(dataObj.getClientId())
                .clientSecretHash(dataObj.getClientSecretHash())
                .authMethod(dataObj.getAuthMethod())
                .scopes(dataObj.getScopes())
                .redirectUri(dataObj.getRedirectUri())
                .status(dataObj.getStatus())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    public static PasskeyCredentialDO toDataObj(PasskeyCredential credential) {
        if (credential == null) {
            return null;
        }
        PasskeyCredentialDO dataObj = new PasskeyCredentialDO();
        dataObj.setId(credential.getId());
        dataObj.setUserId(credential.getUserId());
        dataObj.setCredentialId(credential.getCredentialId());
        dataObj.setPublicKey(credential.getPublicKey());
        dataObj.setSignCount(credential.getSignCount());
        dataObj.setDeviceName(credential.getDeviceName());
        dataObj.setStatus(credential.getStatus());
        dataObj.setLastUsedTime(credential.getLastUsedTime());
        dataObj.setVersion(credential.getVersion());
        dataObj.setCreateTime(credential.getCreateTime());
        dataObj.setUpdateTime(credential.getUpdateTime());
        return dataObj;
    }

    public static PasskeyCredential toDomain(PasskeyCredentialDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return PasskeyCredential.builder()
                .id(dataObj.getId())
                .userId(dataObj.getUserId())
                .credentialId(dataObj.getCredentialId())
                .publicKey(dataObj.getPublicKey())
                .signCount(dataObj.getSignCount())
                .deviceName(dataObj.getDeviceName())
                .status(dataObj.getStatus())
                .lastUsedTime(dataObj.getLastUsedTime())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
