package online.yudream.base.domain.platform.dataviz.repo;

import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;

import java.util.List;

public interface ChartDatasetGateway {

    List<ChartDataSeries> query(ChartDatasetQuery query);
}
