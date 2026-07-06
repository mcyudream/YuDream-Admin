package online.yudream.base.application.platform.dataviz.assembler;

import online.yudream.base.application.platform.dataviz.dto.ChartDatasetDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartDefinitionDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartLinkDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartNodeDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartSeriesDTO;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataLink;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataNode;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;

import java.util.List;

public class ChartAssembler {

    private ChartAssembler() {
    }

    public static ChartDefinitionDTO toDTO(ChartDefinition chart) {
        return ChartDefinitionDTO.builder()
                .id(chart.getId())
                .code(chart.getCode())
                .name(chart.getName())
                .chartType(chart.getChartType())
                .dataSource(chart.getDataSource())
                .queryConfig(chart.getQueryConfig())
                .layoutConfig(chart.getLayoutConfig())
                .enabled(chart.isEnabled())
                .createTime(chart.getCreateTime())
                .updateTime(chart.getUpdateTime())
                .build();
    }

    public static List<ChartDefinitionDTO> toDTOs(List<ChartDefinition> charts) {
        return charts == null ? List.of() : charts.stream().map(ChartAssembler::toDTO).toList();
    }

    public static ChartDatasetDTO toDTO(ChartType chartType, String title, String subTitle, List<ChartDataSeries> series) {
        return ChartDatasetDTO.builder()
                .chartType(chartType)
                .title(title)
                .subTitle(subTitle)
                .series(series == null ? List.of() : series.stream().map(ChartAssembler::toDTO).toList())
                .build();
    }

    public static ChartSeriesDTO toDTO(ChartDataSeries series) {
        return ChartSeriesDTO.builder()
                .name(series.name())
                .categories(series.categories())
                .values(series.values())
                .nodes(toNodeDTOs(series.nodes()))
                .links(toLinkDTOs(series.links()))
                .build();
    }

    private static List<ChartNodeDTO> toNodeDTOs(List<ChartDataNode> nodes) {
        if (nodes == null) {
            return List.of();
        }
        return nodes.stream()
                .map(node -> ChartNodeDTO.builder()
                        .id(node.id())
                        .name(node.name())
                        .group(node.group())
                        .value(node.value())
                        .build())
                .toList();
    }

    private static List<ChartLinkDTO> toLinkDTOs(List<ChartDataLink> links) {
        if (links == null) {
            return List.of();
        }
        return links.stream()
                .map(link -> ChartLinkDTO.builder()
                        .source(link.source())
                        .target(link.target())
                        .value(link.value())
                        .build())
                .toList();
    }
}
