package online.yudream.base.interfaces.platform.dataviz.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;

import java.util.List;

@Data
@Builder
public class ChartDatasetRes {

    private ChartType chartType;
    private String title;
    private String subTitle;
    private List<ChartSeriesRes> series;
}
