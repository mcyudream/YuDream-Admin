package online.yudream.base.infra.platform.dataviz.impl;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.repo.ChartDatasetGateway;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.infra.platform.dataviz.service.ChartDataProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChartDatasetGatewayImpl implements ChartDatasetGateway {

    private final Map<String, ChartDataProvider> providerMap;

    public ChartDatasetGatewayImpl(List<ChartDataProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(
                        ChartDataProvider::source,
                        Function.identity(),
                        (existing, replacement) -> {
                            throw new IllegalStateException(
                                    "Duplicate ChartDataProvider source: " + existing.source());
                        }));
    }

    @Override
    public List<ChartDataSeries> query(ChartDatasetQuery query) {
        ChartDataProvider provider = providerMap.get(query.source());
        if (provider == null) {
            throw new BizException("不支持的数据来源: " + query.source());
        }
        return provider.query(query);
    }
}
