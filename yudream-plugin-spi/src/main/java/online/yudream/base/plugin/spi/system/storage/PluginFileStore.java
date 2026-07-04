package online.yudream.base.plugin.spi.system.storage;

import java.io.InputStream;

public interface PluginFileStore {

    String put(String objectKey, InputStream inputStream, long contentLength, String contentType);

    PluginStoredFile get(String objectKey);

    void delete(String objectKey);
}
