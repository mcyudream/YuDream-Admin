package online.yudream.base.interfaces.platform.dataviz.assembler;

import online.yudream.base.application.platform.dataviz.cmd.ChartDefinitionCreateCmd;
import online.yudream.base.application.platform.dataviz.cmd.ChartDefinitionUpdateCmd;
import online.yudream.base.application.platform.dataviz.dto.ChartDatasetDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartDefinitionDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartLinkDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartNodeDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartSeriesDTO;
import online.yudream.base.application.platform.dataviz.query.ChartDataQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDataRequest;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDefinitionSaveRequest;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDefinitionUpdateRequest;
import online.yudream.base.interfaces.platform.dataviz.res.ChartDatasetRes;
import online.yudream.base.interfaces.platform.dataviz.res.ChartDefinitionRes;
import online.yudream.base.interfaces.platform.dataviz.res.ChartLinkRes;
import online.yudream.base.interfaces.platform.dataviz.res.ChartNodeRes;
import online.yudream.base.interfaces.platform.dataviz.res.ChartSeriesRes;

public class ChartWebAssembler {

    private ChartWebAssembler() {
    }

    public static ChartDefinitionCreateCmd toCmd(ChartDefinitionSaveRequest request) {
        ChartDefinitionCreateCmd cmd = new ChartDefinitionCreateCmd();
        cmd.setCode(request.getCode());
        cmd.setName(request.getName());
        cmd.setChartType(request.getChartType());
        cmd.setDataSource(request.getDataSource());
        cmd.setQueryConfig(request.getQueryConfig());
        cmd.setLayoutConfig(request.getLayoutConfig());
        return cmd;
    }

    public static ChartDefinitionUpdateCmd toCmd(Long id, ChartDefinitionUpdateRequest request) {
        ChartDefinitionUpdateCmd cmd = new ChartDefinitionUpdateCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setChartType(request.getChartType());
        cmd.setDataSource(request.getDataSource());
        cmd.setQueryConfig(request.getQueryConfig());
        cmd.setLayoutConfig(request.getLayoutConfig());
        return cmd;
    }

    public static ChartDataQuery toCmd(ChartDataRequest request) {
        ChartDataQuery query = new ChartDataQuery();
        query.setDefinitionId(request.getDefinitionId());
        query.setChartType(request.getChartType());
        query.setDatasetQuery(request.getDatasetQuery());
        return query;
    }

    public static PageResult<ChartDefinitionRes> toResPage(PageResult<ChartDefinitionDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(ChartWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public static ChartDefinitionRes toRes(ChartDefinitionDTO dto) {
        return ChartDefinitionRes.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .name(dto.getName())
                .chartType(dto.getChartType())
                .dataSource(dto.getDataSource())
                .queryConfig(dto.getQueryConfig())
                .layoutConfig(dto.getLayoutConfig())
                .enabled(dto.isEnabled())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static ChartDatasetRes toRes(ChartDatasetDTO dto) {
        return ChartDatasetRes.builder()
                .chartType(dto.getChartType())
                .title(dto.getTitle())
                .subTitle(dto.getSubTitle())
                .series(dto.getSeries().stream().map(ChartWebAssembler::toRes).toList())
                .build();
    }

    public static ChartSeriesRes toRes(ChartSeriesDTO dto) {
        return ChartSeriesRes.builder()
                .name(dto.getName())
                .categories(dto.getCategories())
                .values(dto.getValues())
                .nodes(dto.getNodes() == null ? null : dto.getNodes().stream().map(ChartWebAssembler::toRes).toList())
                .links(dto.getLinks() == null ? null : dto.getLinks().stream().map(ChartWebAssembler::toRes).toList())
                .build();
    }

    public static ChartNodeRes toRes(ChartNodeDTO dto) {
        return ChartNodeRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .group(dto.getGroup())
                .value(dto.getValue())
                .build();
    }

    public static ChartLinkRes toRes(ChartLinkDTO dto) {
        return ChartLinkRes.builder()
                .source(dto.getSource())
                .target(dto.getTarget())
                .value(dto.getValue())
                .build();
    }
}
