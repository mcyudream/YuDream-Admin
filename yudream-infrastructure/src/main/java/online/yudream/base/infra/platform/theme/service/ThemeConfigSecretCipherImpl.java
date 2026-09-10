package online.yudream.base.infra.platform.theme.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.theme.service.ThemeConfigSecretCipher;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.springframework.stereotype.Component;

/**
 * 主题配置敏感字段加密适配：复用能力凭据 AES-GCM 加密器，
 * AAD 绑定 pluginTheme.config.{themeCode}:{fieldKey}。
 */
@Component
@RequiredArgsConstructor
public class ThemeConfigSecretCipherImpl implements ThemeConfigSecretCipher {

    private final CapabilityCredentialCipher credentialCipher;

    @Override
    public boolean canEncrypt() {
        return credentialCipher.canEncrypt();
    }

    @Override
    public boolean encrypted(String value) {
        return credentialCipher.encrypted(value);
    }

    @Override
    public String encrypt(String themeCode, String fieldKey, String plaintext) {
        return credentialCipher.encryptSecret("pluginTheme.config." + themeCode, fieldKey, plaintext);
    }
}
