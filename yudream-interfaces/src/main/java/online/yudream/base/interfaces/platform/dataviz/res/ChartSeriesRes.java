package online.yudream.base.interfaces.platform.dataviz.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChartSeriesRes {

    private String name;
    private List<String> categories;
    private List<Number> values;
    private List<ChartNodeRes> nodes;
    private List<ChartLinkRes> links;
}
