package online.yudream.base.infra.platform.dataviz.service;

import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

import java.util.List;

public interface ChartDataProvider {

    String source();

    List<ChartDataSeries> query(ChartDatasetQuery query);
}
