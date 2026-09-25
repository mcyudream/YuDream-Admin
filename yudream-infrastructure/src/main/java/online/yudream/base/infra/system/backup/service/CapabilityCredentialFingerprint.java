package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.system.backup.service.CredentialFingerprint;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.springframework.stereotype.Service;

/** 主密钥指纹实现：复用统一凭据加密器的主密钥。 */
@Service
public class CapabilityCredentialFingerprint implements CredentialFingerprint {

    private final CapabilityCredentialCipher cipher;

    public CapabilityCredentialFingerprint(CapabilityCredentialCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String fingerprint() {
        return cipher.fingerprint();
    }
}
