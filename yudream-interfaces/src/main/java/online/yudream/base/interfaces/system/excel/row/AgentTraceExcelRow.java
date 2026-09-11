package online.yudream.base.interfaces.system.excel.row;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AgentTraceExcelRow {
    @ExcelProperty("追踪ID")
    private String traceId;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("来源")
    private String source;
    @ExcelProperty("Agent")
    private String agentName;
    @ExcelProperty("Agent编码")
    private String agentCode;
    @ExcelProperty("插件")
    private String ownerPluginCode;
    @ExcelProperty("步数")
    private Integer stepCount;
    @ExcelProperty("耗时(ms)")
    private Long durationMs;
    @ExcelProperty("输入摘要")
    private String input;
    @ExcelProperty("错误")
    private String error;
    @ExcelProperty("开始时间")
    private String startTime;
}
