package online.yudream.base.application.platform.dataviz.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.dataviz.assembler.ChartAssembler;
import online.yudream.base.application.platform.dataviz.cmd.ChartDefinitionCreateCmd;
import online.yudream.base.application.platform.dataviz.cmd.ChartDefinitionUpdateCmd;
import online.yudream.base.application.platform.dataviz.dto.ChartDatasetDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartDefinitionDTO;
import online.yudream.base.application.platform.dataviz.query.ChartDataQuery;
import online.yudream.base.application.platform.dataviz.query.ChartDefinitionPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.domain.platform.dataviz.repo.ChartDatasetGateway;
import online.yudream.base.domain.platform.dataviz.repo.ChartDefinitionRepo;
import online.yudream.base.domain.platform.dataviz.service.ChartDomainService;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChartAppService {

    private static final String CAPABILITY_CODE = "dataviz";
    private static final String CAPABILITY_NAME = "数据可视化";

    private final CapabilityAppService capabilityAppService;
    private final ChartDomainService chartDomainService;
    private final ChartDefinitionRepo chartDefinitionRepo;
    private final ChartDatasetGateway chartDatasetGateway;

    @Transactional(readOnly = true)
    public PageResult<ChartDefinitionDTO> pageDefinitions(ChartDefinitionPageQuery query) {
        ensureEnabled();
        PageResult<ChartDefinition> page = chartDefinitionRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(
                ChartAssembler.toDTOs(page.getRecords()),
                page.getTotal(),
                page.getPage(),
                page.getSize()
        );
    }

    @Transactional
    public ChartDefinitionDTO createDefinition(ChartDefinitionCreateCmd cmd) {
        ensureEnabled();
        ensureCodeAvailable(cmd.getCode(), null);
        ChartDefinition chart = ChartDefinition.create(
                cmd.getCode(),
                cmd.getName(),
                cmd.getChartType(),
                cmd.getDataSource(),
                cmd.getQueryConfig(),
                cmd.getLayoutConfig()
        );
        return ChartAssembler.toDTO(chartDefinitionRepo.save(chart));
    }

    @Transactional
    public ChartDefinitionDTO updateDefinition(ChartDefinitionUpdateCmd cmd) {
        ensureEnabled();
        ChartDefinition chart = getChart(cmd.getId());
        chart.update(
                cmd.getName(),
                cmd.getChartType(),
                cmd.getDataSource(),
                cmd.getQueryConfig(),
                cmd.getLayoutConfig()
        );
        return ChartAssembler.toDTO(chartDefinitionRepo.save(chart));
    }

    @Transactional
    public void disableDefinition(Long id) {
        ensureEnabled();
        ChartDefinition chart = getChart(id);
        chart.disable();
        chartDefinitionRepo.save(chart);
    }

    @Transactional(readOnly = true)
    public ChartDatasetDTO queryDataset(ChartDataQuery query) {
        ensureEnabled();
        chartDomainService.validateDatasetQuery(query.getChartType(), query.getDatasetQuery());
        List<ChartDataSeries> series = chartDatasetGateway.query(query.getDatasetQuery());
        String title = null;
        String subTitle = null;
        if (query.getDefinitionId() != null) {
            ChartDefinition definition = getChart(query.getDefinitionId());
            if (!definition.getChartType().equals(query.getChartType())) {
                throw new BizException("图表类型与定义不匹配");
            }
            if (definition.getLayoutConfig() != null) {
                title = definition.getLayoutConfig().title();
                subTitle = definition.getLayoutConfig().subTitle();
            }
        }
        return ChartAssembler.toDTO(query.getChartType(), title, subTitle, series);
    }

    private ChartDefinition getChart(Long id) {
        return chartDefinitionRepo.findById(id)
                .orElseThrow(() -> new BizException("图表定义不存在"));
    }

    private void ensureCodeAvailable(String code, Long currentId) {
        chartDefinitionRepo.findByCode(code).ifPresent(existing -> {
            if (currentId == null || !currentId.equals(existing.getId())) {
                throw new BizException("图表编码已存在");
            }
        });
    }

    private void ensureEnabled() {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, CAPABILITY_NAME);
    }
}
