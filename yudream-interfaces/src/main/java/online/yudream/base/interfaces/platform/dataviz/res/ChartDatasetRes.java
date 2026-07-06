package online.yudream.base.interfaces.platform.dataviz.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChartDatasetRes {

    private String chartType;
    private String title;
    private String subTitle;
    private List<ChartSeriesRes> series;
}
