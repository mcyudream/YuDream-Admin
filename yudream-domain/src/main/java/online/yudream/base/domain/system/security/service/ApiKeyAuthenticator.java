package online.yudream.base.domain.system.security.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiKeyCredential;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;

import java.security.MessageDigest;
import java.time.LocalDateTime;

public class ApiKeyAuthenticator {

    public ApiKeyAuthentication authenticate(ApiSecurityPolicy policy, ApiKeyCredential credential, String candidateHash) {
        if (policy == null || !policy.isApiKeyEnabled()) {
            throw new BizException("API Key 认证未启用");
        }
        if (credential == null || credential.getSecret() == null || !credential.activeAt(LocalDateTime.now())) {
            throw new BizException("API Key 无效或已过期");
        }
        if (!MessageDigest.isEqual(
                credential.getSecret().secretHash().getBytes(),
                candidateHash == null ? new byte[0] : candidateHash.getBytes())) {
            throw new BizException("API Key 无效或已过期");
        }
        return new ApiKeyAuthentication(
                credential.getId(),
                credential.getCreatorUserId(),
                credential.getPermissionScope().permissions()
        );
    }
}
