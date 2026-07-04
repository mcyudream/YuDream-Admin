package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.domain.system.file.valobj.StoredObject;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.regex.Pattern;

public class ObjectStoragePluginFileStore implements PluginFileStore {

    private static final Pattern PLUGIN_CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final String pluginCode;
    private final ObjectStorage objectStorage;

    public ObjectStoragePluginFileStore(String pluginCode, ObjectStorage objectStorage) {
        this.pluginCode = requirePluginCode(pluginCode);
        this.objectStorage = objectStorage;
    }

    @Override
    public String put(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        String logicalKey = normalizeObjectKey(objectKey);
        objectStorage.put(storageKey(logicalKey), inputStream, contentLength, contentType);
        return logicalKey;
    }

    @Override
    public PluginStoredFile get(String objectKey) {
        String logicalKey = normalizeObjectKey(objectKey);
        StoredObject object = objectStorage.get(storageKey(logicalKey));
        return new PluginStoredFile(logicalKey, object.contentType(), object.contentLength(), object.inputStream());
    }

    @Override
    public void delete(String objectKey) {
        objectStorage.delete(storageKey(normalizeObjectKey(objectKey)));
    }

    private String storageKey(String logicalKey) {
        return "plugins/" + pluginCode + "/" + logicalKey;
    }

    private String normalizeObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BizException("插件文件路径不能为空");
        }
        String normalized = objectKey.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!StringUtils.hasText(normalized) || normalized.endsWith("/")) {
            throw new BizException("插件文件路径不合法：" + objectKey);
        }
        if (Arrays.stream(normalized.split("/")).anyMatch(this::invalidPathSegment)) {
            throw new BizException("插件文件路径不合法：" + objectKey);
        }
        return normalized;
    }

    private boolean invalidPathSegment(String segment) {
        return !StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment);
    }

    private String requirePluginCode(String pluginCode) {
        if (!StringUtils.hasText(pluginCode) || !PLUGIN_CODE_PATTERN.matcher(pluginCode).matches()) {
            throw new BizException("插件编码不合法：" + pluginCode);
        }
        return pluginCode.trim();
    }
}
