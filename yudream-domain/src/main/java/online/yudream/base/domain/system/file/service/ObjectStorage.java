package online.yudream.base.domain.system.file.service;

import online.yudream.base.domain.system.file.valobj.StoredObject;

import java.io.InputStream;

public interface ObjectStorage {

    String bucket();

    String put(String objectKey, InputStream inputStream, long contentLength, String contentType);

    StoredObject get(String objectKey);

    /**
     * 读取 [start, end] 闭区间字节（媒体播放与拖动进度用）。默认实现读取整个对象并跳过前缀，
     * 支持原生范围读取的存储应覆盖。返回的 contentLength 为本段长度。
     */
    default StoredObject get(String objectKey, long start, long end) {
        StoredObject whole = get(objectKey);
        try {
            whole.inputStream().skipNBytes(start);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取文件片段失败", e);
        }
        long length = end - start + 1;
        return new StoredObject(objectKey, whole.contentType(), length, new java.io.FilterInputStream(whole.inputStream()) {
            private long remaining = length;

            @Override
            public int read() throws java.io.IOException {
                if (remaining <= 0) {
                    return -1;
                }
                int value = super.read();
                if (value >= 0) {
                    remaining--;
                }
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int count) throws java.io.IOException {
                if (remaining <= 0) {
                    return -1;
                }
                int read = super.read(buffer, offset, (int) Math.min(count, remaining));
                if (read > 0) {
                    remaining -= read;
                }
                return read;
            }
        });
    }

    void delete(String objectKey);
}
