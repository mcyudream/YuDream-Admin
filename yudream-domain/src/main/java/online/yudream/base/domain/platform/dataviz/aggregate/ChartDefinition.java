package online.yudream.base.domain.platform.dataviz.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ChartDefinition extends BaseDomain {

    private String code;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
    private boolean enabled;

    public static ChartDefinition create(String code, String name, ChartType chartType, String dataSource,
                                         ChartDatasetQuery queryConfig, ChartLayoutConfig layoutConfig) {
        ChartDefinition chart = new ChartDefinition();
        chart.code = required(code, "图表编码不能为空");
        chart.name = required(name, "图表名称不能为空");
        if (chartType == null) {
            throw new BizException("图表类型不能为空");
        }
        chart.chartType = chartType;
        chart.dataSource = required(dataSource, "数据源不能为空");
        if (queryConfig == null) {
            throw new BizException("查询配置不能为空");
        }
        chart.queryConfig = queryConfig;
        if (layoutConfig == null) {
            throw new BizException("布局配置不能为空");
        }
        chart.layoutConfig = layoutConfig;
        chart.enabled = true;
        return chart;
    }

    public void update(String name, ChartType chartType, String dataSource,
                       ChartDatasetQuery queryConfig, ChartLayoutConfig layoutConfig) {
        update(name, chartType, dataSource, queryConfig, layoutConfig, this.enabled);
    }

    public void update(String name, ChartType chartType, String dataSource,
                       ChartDatasetQuery queryConfig, ChartLayoutConfig layoutConfig, Boolean enabled) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (chartType != null) {
            this.chartType = chartType;
        }
        if (dataSource != null && !dataSource.isBlank()) {
            this.dataSource = dataSource.trim();
        }
        if (queryConfig != null) {
            if (queryConfig.source() == null || queryConfig.source().isBlank()) {
                throw new BizException("数据源不能为空");
            }
            this.queryConfig = queryConfig;
        }
        if (layoutConfig != null) {
            this.layoutConfig = layoutConfig;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
    }

    public void disable() {
        this.enabled = false;
    }

    public void activate() {
        this.enabled = true;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
