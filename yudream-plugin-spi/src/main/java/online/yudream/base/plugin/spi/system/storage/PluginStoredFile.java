package online.yudream.base.plugin.spi.system.storage;

import java.io.InputStream;

public record PluginStoredFile(
        String objectKey,
        String contentType,
        Long contentLength,
        InputStream inputStream
) {
}
