package online.yudream.base.domain.system.security.valobj;

public record PasskeyAuthenticationResult(
        Long userId,
        String username,
        String credentialId,
        long signCount
) {
}
