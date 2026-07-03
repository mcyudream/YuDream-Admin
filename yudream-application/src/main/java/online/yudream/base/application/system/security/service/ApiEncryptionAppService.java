package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.dto.ApiEncryptedPayloadDTO;
import online.yudream.base.application.system.security.dto.ApiEncryptionPublicKeyDTO;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.service.ApiPayloadEncryptionGateway;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApiEncryptionAppService {

    private static final String ALGORITHM = "RSA-OAEP/AES-GCM";

    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;
    private final ApiPayloadEncryptionGateway apiPayloadEncryptionGateway;

    public ApiEncryptionPublicKeyDTO publicKey() {
        boolean enabled = enabled();
        return ApiEncryptionPublicKeyDTO.builder()
                .enabled(enabled)
                .algorithm(ALGORITHM)
                .publicKey(enabled ? apiPayloadEncryptionGateway.publicKey() : null)
                .build();
    }

    public boolean enabled() {
        return currentPolicy().isApiEncryptionEnabled();
    }

    public byte[] decryptSessionKey(String encryptedSessionKey) {
        return apiPayloadEncryptionGateway.decryptSessionKey(encryptedSessionKey);
    }

    public String decrypt(byte[] sessionKey, String iv, String encryptedPayload) {
        return apiPayloadEncryptionGateway.decrypt(sessionKey, iv, encryptedPayload);
    }

    public ApiEncryptedPayloadDTO encrypt(byte[] sessionKey, String payload) {
        String iv = apiPayloadEncryptionGateway.newIv();
        return ApiEncryptedPayloadDTO.builder()
                .iv(iv)
                .data(apiPayloadEncryptionGateway.encrypt(sessionKey, iv, payload))
                .build();
    }

    private ApiSecurityPolicy currentPolicy() {
        return apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
    }
}
