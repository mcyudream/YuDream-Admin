package online.yudream.base.infra.platform.dataviz.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CapabilityChartDataProvider implements ChartDataProvider {

    private final CapabilityModuleRepo capabilityModuleRepo;

    @Override
    public String source() {
        return "capability";
    }

    @Override
    public List<ChartDataSeries> query(ChartDatasetQuery query) {
        Map<String, Long> counts = capabilityModuleRepo.findAll().stream()
                .collect(Collectors.groupingBy(
                        module -> module.getType() == null ? "UNKNOWN" : module.getType().name(),
                        Collectors.counting()
                ));
        List<String> categories = counts.keySet().stream().sorted().toList();
        List<Number> values = categories.stream().<Number>map(counts::get).toList();
        return List.of(new ChartDataSeries("capability-count", categories, values, null, null));
    }
}
