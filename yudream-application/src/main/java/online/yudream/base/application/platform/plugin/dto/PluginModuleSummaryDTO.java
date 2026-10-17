package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** 插件目录概览：分页后 records 只覆盖当前页，状态计数胶囊需要全量分布。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginModuleSummaryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long total;

    /** 运行状态 -> 数量；未出现过的状态计 0，前端筛选胶囊可直接渲染。 */
    @Builder.Default
    private Map<String, Long> statusCounts = new LinkedHashMap<>();
}
