package online.yudream.base.domain.platform.plugin.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceSyncStatus;

import java.time.LocalDateTime;

/**
 * 插件市场源。内置源（builtIn）的 rootUrl 由宿主配置镜像，管理端只能调整令牌、启停与名称；
 * 普通源的全部字段归管理端。token 仅在领域/应用层流转，接口层不得下发。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketSource extends BaseDomain {

    private String code;
    private String name;
    private String rootUrl;
    private String token;
    private Boolean enabled;
    private Boolean builtIn;
    private Integer sortOrder;
    private MarketSourceSyncStatus syncStatus;
    private String syncErrorMessage;
    private LocalDateTime syncedAt;

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean builtIn() {
        return Boolean.TRUE.equals(builtIn);
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void markSynced() {
        this.syncStatus = MarketSourceSyncStatus.OK;
        this.syncErrorMessage = null;
        this.syncedAt = LocalDateTime.now();
    }

    public void markSyncError(String message) {
        this.syncStatus = MarketSourceSyncStatus.ERROR;
        this.syncErrorMessage = message;
        this.syncedAt = LocalDateTime.now();
    }
}
