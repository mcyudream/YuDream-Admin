package online.yudream.base.application.platform.devtools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行追踪分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTracePageDTO {

    private long total;
    private int page;
    private int size;

    @Builder.Default
    private List<AgentTraceSummaryDTO> list = new ArrayList<>();
}
