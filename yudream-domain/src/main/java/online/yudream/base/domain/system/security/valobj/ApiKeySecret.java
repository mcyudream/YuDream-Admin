package online.yudream.base.domain.system.security.valobj;

import online.yudream.base.domain.common.exception.BizException;

public record ApiKeySecret(String prefix, String secretHash, String maskedValue) {

    public ApiKeySecret {
        if (prefix == null || prefix.isBlank()) {
            throw new BizException("API Key 前缀不能为空");
        }
        if (secretHash == null || secretHash.isBlank()) {
            throw new BizException("API Key 密文不能为空");
        }
        prefix = prefix.trim();
        secretHash = secretHash.trim();
        maskedValue = maskedValue == null || maskedValue.isBlank() ? mask(prefix) : maskedValue.trim();
    }

    public static String mask(String prefix) {
        return prefix + "****";
    }
}
