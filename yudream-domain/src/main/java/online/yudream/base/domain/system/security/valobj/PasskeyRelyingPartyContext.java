package online.yudream.base.domain.system.security.valobj;

import online.yudream.base.domain.common.exception.BizException;

public record PasskeyRelyingPartyContext(
        String rpId,
        String origin,
        String rpName
) {
    public PasskeyRelyingPartyContext {
        rpId = requireText(rpId, "Passkey RP ID 不能为空");
        origin = requireText(origin, "Passkey Origin 不能为空");
        rpName = hasText(rpName) ? rpName.trim() : "YuDream Admin";
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
