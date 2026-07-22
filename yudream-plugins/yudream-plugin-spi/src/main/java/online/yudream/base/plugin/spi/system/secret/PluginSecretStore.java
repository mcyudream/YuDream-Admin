package online.yudream.base.plugin.spi.system.secret;

import java.util.Optional;

/** Plugin-scoped opaque secret storage. */
public interface PluginSecretStore {
    void put(String key, byte[] secret);
    Optional<byte[]> get(String key);
    boolean delete(String key);
}
