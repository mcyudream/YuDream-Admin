package online.yudream.base.interfaces.platform.dataviz.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartNodeRes {

    private String id;
    private String name;
    private String group;
    private Number value;
}
