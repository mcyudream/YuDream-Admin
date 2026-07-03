package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiKeyCredential;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.repo.ApiKeyCredentialRepo;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticator;
import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ApiKeyAuthAppService {

    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;
    private final ApiKeyCredentialRepo apiKeyCredentialRepo;
    private final ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator();

    @Transactional
    public ApiKeyAuthentication authenticate(String plaintext) {
        String prefix = prefixOf(plaintext);
        ApiSecurityPolicy policy = apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
        ApiKeyCredential credential = apiKeyCredentialRepo.findByPrefix(prefix)
                .orElseThrow(() -> new BizException("API Key 无效或已过期"));
        ApiKeyAuthentication authentication = authenticator.authenticate(policy, credential, ApiKeySecretHasher.hash(plaintext));
        credential.markUsed(LocalDateTime.now());
        apiKeyCredentialRepo.save(credential);
        return authentication;
    }

    private String prefixOf(String plaintext) {
        if (plaintext == null || !plaintext.startsWith("yda_") || plaintext.length() < 12) {
            throw new BizException("API Key 无效或已过期");
        }
        return plaintext.substring(0, 12);
    }
}
