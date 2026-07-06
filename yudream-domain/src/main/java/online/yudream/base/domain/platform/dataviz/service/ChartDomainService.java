package online.yudream.base.domain.platform.dataviz.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

public class ChartDomainService {

    public void validateDatasetQuery(ChartType chartType, ChartDatasetQuery query) {
        if (chartType == null) {
            throw new BizException("图表类型不能为空");
        }
        if (query == null) {
            throw new BizException("查询配置不能为空");
        }
        if (query.source() == null || query.source().isBlank()) {
            throw new BizException("数据来源不能为空");
        }
        if (query.metric() == null || query.metric().isBlank()) {
            throw new BizException("数据指标不能为空");
        }
    }
}
