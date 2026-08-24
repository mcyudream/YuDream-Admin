package online.yudream.base.domain.platform.graph.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 逻辑图表聚合。类名保留仅用于 Mongo 历史记录兼容；不会承载任何物理连接或凭据。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GraphConnection extends BaseDomain {
    private String name;
    private String code;
    private String description;
    private GraphConnectionStatus status;
    private Set<String> authorizedPluginCodes;

    public static GraphConnection create(String name, String code, String description) {
        GraphConnection table = new GraphConnection();
        table.name = required(name, "逻辑图表名称不能为空");
        table.code = required(code, "逻辑图表编码不能为空");
        table.description = text(description);
        table.status = GraphConnectionStatus.ACTIVE;
        return table;
    }

    public void update(String name, String description, GraphConnectionStatus status) {
        this.name = required(name, "逻辑图表名称不能为空");
        this.description = text(description);
        this.status = status == null ? GraphConnectionStatus.ACTIVE : status;
    }

    public void disable() { this.status = GraphConnectionStatus.DISABLED; }
    public void activate() { this.status = GraphConnectionStatus.ACTIVE; }
    public boolean active() { return GraphConnectionStatus.ACTIVE == status; }

    public boolean authorizedFor(String pluginCode) {
        return pluginCode != null && !pluginCode.isBlank() && normalizedAuthorizedPluginCodes().contains(pluginCode.trim());
    }

    public void replaceAuthorizedPlugins(Set<String> pluginCodes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (pluginCodes != null) {
            pluginCodes.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).forEach(normalized::add);
        }
        this.authorizedPluginCodes = normalized;
    }

    public Set<String> normalizedAuthorizedPluginCodes() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (authorizedPluginCodes != null) {
            authorizedPluginCodes.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).forEach(normalized::add);
        }
        return Set.copyOf(normalized);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new BizException(message);
        return value.trim();
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
