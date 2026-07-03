package online.yudream.base.domain.system.file.valobj;

import java.io.InputStream;

public record StoredObject(
        String objectKey,
        String contentType,
        Long contentLength,
        InputStream inputStream
) {
}
