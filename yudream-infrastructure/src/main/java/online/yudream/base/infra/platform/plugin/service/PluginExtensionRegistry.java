package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PluginExtensionRegistry {

    private final ConcurrentMap<String, ConcurrentMap<Class<?>, Object>> extensions = new ConcurrentHashMap<>();

    public <T> void register(String pluginCode, Class<T> type, T extension) {
        String safePluginCode = requirePluginCode(pluginCode);
        if (type == null) {
            throw new BizException("插件扩展类型不能为空");
        }
        if (extension == null) {
            throw new BizException("插件扩展实例不能为空");
        }
        extensions.computeIfAbsent(safePluginCode, ignored -> new ConcurrentHashMap<>()).put(type, extension);
    }

    public <T> Optional<T> find(String pluginCode, Class<T> type) {
        if (type == null) {
            return Optional.empty();
        }
        Map<Class<?>, Object> pluginExtensions = extensions.get(pluginCode);
        if (pluginExtensions == null) {
            return Optional.empty();
        }
        Object extension = pluginExtensions.get(type);
        return extension == null ? Optional.empty() : Optional.of(type.cast(extension));
    }

    public <T> List<T> findAll(Class<T> type) {
        if (type == null) {
            return List.of();
        }
        return extensions.values().stream()
                .map(pluginExtensions -> pluginExtensions.get(type))
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    public void clear(String pluginCode) {
        if (StringUtils.hasText(pluginCode)) {
            extensions.remove(pluginCode);
        }
    }

    private String requirePluginCode(String pluginCode) {
        if (!StringUtils.hasText(pluginCode)) {
            throw new BizException("插件编码不能为空");
        }
        return pluginCode.trim();
    }
}
