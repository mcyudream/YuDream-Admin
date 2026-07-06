package online.yudream.base.infra.platform.dataviz.mapper;

import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.infra.platform.dataviz.dataobj.ChartDefinitionDO;

public class ChartInfraMapper {

    private ChartInfraMapper() {
    }

    public static ChartDefinitionDO toDataObj(ChartDefinition chartDefinition) {
        if (chartDefinition == null) {
            return null;
        }
        ChartDefinitionDO dataObj = new ChartDefinitionDO();
        dataObj.setId(chartDefinition.getId());
        dataObj.setCode(chartDefinition.getCode());
        dataObj.setName(chartDefinition.getName());
        dataObj.setChartType(chartDefinition.getChartType());
        dataObj.setDataSource(chartDefinition.getDataSource());
        dataObj.setQueryConfig(chartDefinition.getQueryConfig());
        dataObj.setLayoutConfig(chartDefinition.getLayoutConfig());
        dataObj.setEnabled(chartDefinition.isEnabled());
        dataObj.setVersion(chartDefinition.getVersion());
        dataObj.setCreateTime(chartDefinition.getCreateTime());
        dataObj.setUpdateTime(chartDefinition.getUpdateTime());
        return dataObj;
    }

    public static ChartDefinition toDomain(ChartDefinitionDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return ChartDefinition.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .name(dataObj.getName())
                .chartType(dataObj.getChartType())
                .dataSource(dataObj.getDataSource())
                .queryConfig(dataObj.getQueryConfig())
                .layoutConfig(dataObj.getLayoutConfig())
                .enabled(dataObj.isEnabled())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
