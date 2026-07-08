package online.yudream.base.plugin.minecraft.domain.valobj;

import online.yudream.base.plugin.minecraft.domain.enumerate.MinecraftEdition;

import java.util.Locale;
import java.util.UUID;

public record MinecraftServerEndpoint(
        String id,
        String name,
        String host,
        int port,
        MinecraftEdition edition,
        boolean primaryLine,
        boolean enabled,
        int sort
) {

    public MinecraftServerEndpoint {
        MinecraftEdition resolvedEdition = edition == null ? MinecraftEdition.JAVA : edition;
        id = normalizeId(id);
        name = normalizeName(name);
        host = normalizeHost(host);
        port = normalizePort(port, resolvedEdition);
        edition = resolvedEdition;
    }

    public String address() {
        return automaticPort() ? host : host + ":" + port;
    }

    public boolean automaticPort() {
        return port <= 0;
    }

    public String cacheKey() {
        return edition.name() + ":" + host.toLowerCase(Locale.ROOT) + ":" + (automaticPort() ? "AUTO" : port);
    }

    private static String normalizeId(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static String normalizeName(String value) {
        return value == null || value.isBlank() ? "默认线路" : value.trim();
    }

    private static String normalizeHost(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("服务器线路地址不能为空");
        }
        String host = value.trim();
        if (host.length() > 255 || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("服务器线路地址格式不正确");
        }
        return host;
    }

    private static int normalizePort(int value, MinecraftEdition edition) {
        if (value <= 0) {
            return edition == MinecraftEdition.JAVA ? 0 : edition.defaultPort();
        }
        if (value > 65535) {
            throw new IllegalArgumentException("服务器线路端口必须在 1-65535 之间");
        }
        return value;
    }
}
