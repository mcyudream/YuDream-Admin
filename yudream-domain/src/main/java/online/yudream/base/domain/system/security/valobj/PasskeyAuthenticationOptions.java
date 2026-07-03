package online.yudream.base.domain.system.security.valobj;

public record PasskeyAuthenticationOptions(
        String requestJson,
        String publicKeyJson
) {
}
