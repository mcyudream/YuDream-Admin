package online.yudream.base.plugin.minecraft.domain.enumerate;

import java.util.Locale;

public enum MinecraftEdition {
    JAVA(25565),
    BEDROCK(19132);

    private final int defaultPort;

    MinecraftEdition(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return defaultPort;
    }

    public static MinecraftEdition of(String value) {
        if (value == null || value.isBlank()) {
            return JAVA;
        }
        return MinecraftEdition.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
