package online.yudream.base.application.platform.graph.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import java.time.LocalDateTime;
import java.util.Set;

/** Logical graph-table DTO. */
@Data
@Builder
public class GraphConnectionDTO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private GraphConnectionStatus status;
    private Set<String> authorizedPluginCodes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
