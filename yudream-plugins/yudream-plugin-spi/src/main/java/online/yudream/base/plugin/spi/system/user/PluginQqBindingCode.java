package online.yudream.base.plugin.spi.system.user;

import java.time.Instant;

public record PluginQqBindingCode(String code, Instant expiresAt) {
}
