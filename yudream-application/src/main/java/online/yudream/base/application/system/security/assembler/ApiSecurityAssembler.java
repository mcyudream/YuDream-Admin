package online.yudream.base.application.system.security.assembler;

import online.yudream.base.application.system.security.dto.ApiKeyCredentialDTO;
import online.yudream.base.application.system.security.dto.ApiSecurityPolicyDTO;
import online.yudream.base.domain.system.security.aggregate.ApiKeyCredential;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.valobj.TokenPolicy;

import java.util.ArrayList;

public class ApiSecurityAssembler {

    private ApiSecurityAssembler() {
    }

    public static ApiSecurityPolicyDTO toDTO(ApiSecurityPolicy policy) {
        if (policy == null) {
            return null;
        }
        TokenPolicy tokenPolicy = policy.getTokenPolicy() == null ? TokenPolicy.defaultPolicy() : policy.getTokenPolicy();
        return ApiSecurityPolicyDTO.builder()
                .id(policy.getId())
                .apiEncryptionEnabled(policy.isApiEncryptionEnabled())
                .dualTokenEnabled(policy.isDualTokenEnabled())
                .apiKeyEnabled(policy.isApiKeyEnabled())
                .passkeyEnabled(policy.isPasskeyEnabled())
                .oauthServerEnabled(policy.isOauthServerEnabled())
                .oauthClientEnabled(policy.isOauthClientEnabled())
                .accessTokenTtlSeconds(tokenPolicy.accessTokenTtlSeconds())
                .refreshTokenTtlSeconds(tokenPolicy.refreshTokenTtlSeconds())
                .refreshRotationEnabled(tokenPolicy.refreshRotationEnabled())
                .updateTime(policy.getUpdateTime())
                .build();
    }

    public static ApiKeyCredentialDTO toDTO(ApiKeyCredential credential) {
        if (credential == null) {
            return null;
        }
        return ApiKeyCredentialDTO.builder()
                .id(credential.getId())
                .name(credential.getName())
                .keyPrefix(credential.getSecret() == null ? null : credential.getSecret().prefix())
                .maskedValue(credential.getSecret() == null ? null : credential.getSecret().maskedValue())
                .creatorUserId(credential.getCreatorUserId())
                .permissions(credential.getPermissionScope() == null
                        ? new ArrayList<>()
                        : credential.getPermissionScope().permissions())
                .expireTime(credential.getExpireTime())
                .status(credential.getStatus())
                .lastUsedTime(credential.getLastUsedTime())
                .createTime(credential.getCreateTime())
                .updateTime(credential.getUpdateTime())
                .build();
    }
}
