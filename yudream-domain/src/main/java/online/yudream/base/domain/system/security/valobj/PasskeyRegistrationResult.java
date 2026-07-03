package online.yudream.base.domain.system.security.valobj;

public record PasskeyRegistrationResult(
        String credentialId,
        String publicKey,
        long signCount
) {
}
