package online.yudream.base.plugin.spi.ws;

import online.yudream.base.plugin.spi.system.security.PluginPrincipal;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手上下文（不可变）。path 为插件内相对路径。
 *
 * <p>不变式：{@code principal} 永不为 null（匿名连接 userId 为 null、permissions 为空），
 * 构造时强制校验；headers/query 深拷贝（List 值同样复制），后续修改原集合不影响本对象。
 * 宿主传入前已剔除敏感头（Authorization/Cookie 等）与票据查询参数。
 */
public record PluginWsHandshake(
        String path,
        Map<String, List<String>> headers,
        Map<String, List<String>> query,
        PluginPrincipal principal
) {
    public PluginWsHandshake {
        headers = copyOf(headers);
        query = copyOf(query);
        if (principal == null) {
            throw new IllegalArgumentException("PluginWsHandshake principal 不能为空");
        }
    }

    private static Map<String, List<String>> copyOf(Map<String, List<String>> source) {
        if (source == null) {
            return Map.of();
        }
        var builder = new java.util.LinkedHashMap<String, List<String>>();
        source.forEach((key, values) -> builder.put(key, values == null ? List.of() : List.copyOf(values)));
        return Map.copyOf(builder);
    }
}
