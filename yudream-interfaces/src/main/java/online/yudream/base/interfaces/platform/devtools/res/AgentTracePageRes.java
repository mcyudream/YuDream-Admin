package online.yudream.base.interfaces.platform.devtools.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行追踪分页响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTracePageRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long total;
    private int page;
    private int size;

    @Builder.Default
    private List<AgentTraceSummaryRes> list = new ArrayList<>();
}
