package online.yudream.base.domain.system.file.service;

import online.yudream.base.domain.system.file.valobj.StoredObject;

import java.io.InputStream;

public interface ObjectStorage {

    String bucket();

    String put(String objectKey, InputStream inputStream, long contentLength, String contentType);

    StoredObject get(String objectKey);

    void delete(String objectKey);
}
