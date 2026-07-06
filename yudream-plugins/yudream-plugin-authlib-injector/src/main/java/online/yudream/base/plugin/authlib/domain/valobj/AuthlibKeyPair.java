package online.yudream.base.plugin.authlib.domain.valobj;

public record AuthlibKeyPair(
        String publicKey,
        String privateKey
) {
}
