package online.yudream.base.application.platform.graph.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class GraphConnectionDTO {
    private Long id;
    private String name;
    private String code;
    private String uri;
    private String username;
    private String database;
    private GraphConnectionStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
