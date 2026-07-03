package online.yudream.base.infra.system.security.mapper;

import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.valobj.TokenPolicy;
import online.yudream.base.infra.system.security.dataobj.ApiSecurityPolicyDO;

public class ApiSecurityPolicyInfraMapper {

    public static ApiSecurityPolicyDO toDataObj(ApiSecurityPolicy policy) {
        if (policy == null) {
            return null;
        }
        ApiSecurityPolicyDO dataObj = new ApiSecurityPolicyDO();
        dataObj.setId(policy.getId());
        dataObj.setCode(policy.getCode());
        dataObj.setApiEncryptionEnabled(policy.isApiEncryptionEnabled());
        dataObj.setDualTokenEnabled(policy.isDualTokenEnabled());
        dataObj.setApiKeyEnabled(policy.isApiKeyEnabled());
        dataObj.setPasskeyEnabled(policy.isPasskeyEnabled());
        dataObj.setOauthServerEnabled(policy.isOauthServerEnabled());
        dataObj.setOauthClientEnabled(policy.isOauthClientEnabled());
        TokenPolicy tokenPolicy = policy.getTokenPolicy() == null ? TokenPolicy.defaultPolicy() : policy.getTokenPolicy();
        dataObj.setAccessTokenTtlSeconds(tokenPolicy.accessTokenTtlSeconds());
        dataObj.setRefreshTokenTtlSeconds(tokenPolicy.refreshTokenTtlSeconds());
        dataObj.setRefreshRotationEnabled(tokenPolicy.refreshRotationEnabled());
        dataObj.setVersion(policy.getVersion());
        dataObj.setCreateTime(policy.getCreateTime());
        dataObj.setUpdateTime(policy.getUpdateTime());
        return dataObj;
    }

    public static ApiSecurityPolicy toDomain(ApiSecurityPolicyDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return ApiSecurityPolicy.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .apiEncryptionEnabled(dataObj.isApiEncryptionEnabled())
                .dualTokenEnabled(dataObj.isDualTokenEnabled())
                .apiKeyEnabled(dataObj.isApiKeyEnabled())
                .passkeyEnabled(dataObj.isPasskeyEnabled())
                .oauthServerEnabled(dataObj.isOauthServerEnabled())
                .oauthClientEnabled(dataObj.isOauthClientEnabled())
                .tokenPolicy(new TokenPolicy(
                        dataObj.getAccessTokenTtlSeconds(),
                        dataObj.getRefreshTokenTtlSeconds(),
                        dataObj.isRefreshRotationEnabled()
                ))
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
