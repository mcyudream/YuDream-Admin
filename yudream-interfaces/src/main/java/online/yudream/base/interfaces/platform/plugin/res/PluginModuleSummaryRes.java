package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** 插件目录概览：总数与运行状态分布，供筛选胶囊计数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginModuleSummaryRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long total;

    @Builder.Default
    private Map<String, Long> statusCounts = new LinkedHashMap<>();
}
