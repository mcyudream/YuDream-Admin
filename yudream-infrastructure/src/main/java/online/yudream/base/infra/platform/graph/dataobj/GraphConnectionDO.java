package online.yudream.base.infra.platform.graph.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Set;

/** Persistent logical graph table. Historical connection secrets are never stored here. */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformGraphTable")
@CompoundIndex(name = "graph_table_active_plugin", def = "{'status': 1, 'authorizedPluginCodes': 1}")
public class GraphConnectionDO extends BaseDO {
    private String name;
    @Indexed(unique = true)
    private String code;
    private String description;
    private GraphConnectionStatus status;
    private Set<String> authorizedPluginCodes;
}
