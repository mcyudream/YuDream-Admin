package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceSyncStatus;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketSourceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private MarketSourceType type;
    private String rootUrl;
    /** 是否已配置访问令牌；令牌本身绝不出域。 */
    private boolean tokenConfigured;
    private boolean enabled;
    private boolean builtIn;
    private Integer sortOrder;
    /** null 表示从未同步。 */
    private MarketSourceSyncStatus syncStatus;
    private String syncErrorMessage;
    private LocalDateTime syncedAt;
    private Integer pluginCount;
}
