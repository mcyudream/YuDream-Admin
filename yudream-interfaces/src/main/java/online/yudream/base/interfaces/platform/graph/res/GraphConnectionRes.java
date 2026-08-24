package online.yudream.base.interfaces.platform.graph.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import java.time.LocalDateTime;
import java.util.Set;

/** Logical graph-table response. */
@Data @Builder
public class GraphConnectionRes {
    private Long id; private String name; private String code; private String description;
    private GraphConnectionStatus status; private Set<String> authorizedPluginCodes;
    private LocalDateTime createTime; private LocalDateTime updateTime;
}
