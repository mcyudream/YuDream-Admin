package online.yudream.base.application.platform.agent.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Agent 执行流分页查询。
 * <p>
 * 过滤语义沿用仓储既有实现：keyword 命中 Agent / 插件 / 追踪 ID / 输入 / 错误；
 * 时间范围按开始日期 00:00:00 至结束日期 23:59:59 收口，前端表格页按 @Searchable 注解生成筛选表单。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AgentTracePageQuery extends PageQuery {
    @Searchable(
        label = "关键词",
        placeholder = "Agent / 插件 / 追踪 ID / 输入 / 错误",
        showWhenFolded = true,
        order = 1,
        width = "260px"
    )
    private String keyword;

    @Searchable(
        label = "来源",
        controlType = ControlType.SELECT,
        optionsSource = "agentTraceSourceOptions",
        order = 2
    )
    private AgentTraceSource source;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        optionsSource = "agentTraceStatusOptions",
        order = 3
    )
    private AgentTraceStatus status;

    @Searchable(
        label = "Agent 编码",
        placeholder = "Agent 编码",
        order = 4
    )
    private String agentCode;

    @Searchable(
        label = "插件编码",
        placeholder = "插件编码",
        order = 5
    )
    private String pluginCode;

    /** 前端日期范围展示字段；实际区间由 startTime / endTime 接收 */
    @Searchable(
        label = "时间范围",
        controlType = ControlType.DATE_RANGE,
        order = 6
    )
    private LocalDate timeRange;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endTime;

    /**
     * 转成领域查询对象，分页参数沿用 PageQuery。
     */
    public AgentTraceQuery toTraceQuery() {
        LocalDateTime start = startTime == null ? null : startTime.atStartOfDay();
        LocalDateTime end = endTime == null ? null : endTime.atTime(LocalTime.MAX);
        return AgentTraceQuery.of(source, pluginCode, status, keyword, agentCode, start, end, getPage(), getSize());
    }
}
