package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceSyncStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketSourceRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String code;
    private String name;
    private String rootUrl;
    private boolean tokenConfigured;
    private boolean enabled;
    private boolean builtIn;
    private Integer sortOrder;
    private MarketSourceSyncStatus syncStatus;
    private String syncErrorMessage;
    private LocalDateTime syncedAt;
    private Integer pluginCount;
}
