package online.yudream.base.infra.platform.plugin.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceSyncStatus;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** token 落库前由仓储实现加密（AAD 含 sourceId），加载时解密；禁止直接对外返回。 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformPluginMarketSource")
public class PluginMarketSourceDO extends BaseDO {

    @Indexed(unique = true)
    private String code;
    private String name;
    private MarketSourceType type;
    private String rootUrl;
    private String token;
    private Boolean enabled;
    private Boolean builtIn;
    private Integer sortOrder;
    private MarketSourceSyncStatus syncStatus;
    private String syncErrorMessage;
    private LocalDateTime syncedAt;
}
