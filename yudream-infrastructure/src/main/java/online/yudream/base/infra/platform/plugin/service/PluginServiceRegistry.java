package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PluginServiceRegistry {

    private final ConcurrentMap<String, ConcurrentMap<Class<?>, Object>> services = new ConcurrentHashMap<>();

    public <T> void export(String pluginCode, Class<T> serviceType, T service) {
        String code = requirePluginCode(pluginCode);
        if (serviceType == null || service == null || !serviceType.isInstance(service)) {
            throw new BizException("插件服务类型与实现不匹配");
        }
        Object previous = services.computeIfAbsent(code, ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(serviceType, service);
        if (previous != null) {
            throw new BizException("插件服务重复导出: " + code + " / " + serviceType.getName());
        }
    }

    public <T> Optional<T> find(String pluginCode, Class<T> serviceType) {
        if (!StringUtils.hasText(pluginCode) || serviceType == null) {
            return Optional.empty();
        }
        Map<Class<?>, Object> pluginServices = services.get(pluginCode.trim());
        if (pluginServices == null) {
            return Optional.empty();
        }
        Object service = pluginServices.get(serviceType);
        return serviceType.isInstance(service) ? Optional.of(serviceType.cast(service)) : Optional.empty();
    }

    public <T> List<T> findAll(Class<T> serviceType) {
        if (serviceType == null) {
            return List.of();
        }
        return services.values().stream()
                .map(pluginServices -> pluginServices.get(serviceType))
                .filter(serviceType::isInstance)
                .map(serviceType::cast)
                .toList();
    }

    public void clear(String pluginCode) {
        if (StringUtils.hasText(pluginCode)) {
            services.remove(pluginCode.trim());
        }
    }

    private String requirePluginCode(String pluginCode) {
        if (!StringUtils.hasText(pluginCode)) {
            throw new BizException("插件编码不能为空");
        }
        return pluginCode.trim();
    }
}
