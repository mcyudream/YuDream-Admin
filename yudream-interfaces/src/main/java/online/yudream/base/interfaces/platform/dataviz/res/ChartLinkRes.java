package online.yudream.base.interfaces.platform.dataviz.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartLinkRes {

    private String source;
    private String target;
    private Number value;
}
