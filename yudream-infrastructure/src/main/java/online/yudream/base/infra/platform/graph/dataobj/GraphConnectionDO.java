package online.yudream.base.infra.platform.graph.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformGraphConnection")
public class GraphConnectionDO extends BaseDO {
    private String name;
    @Indexed(unique = true)
    private String code;
    private String uri;
    private String username;
    private String password;
    private String database;
    private GraphConnectionStatus status;
}
