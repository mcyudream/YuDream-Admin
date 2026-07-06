package online.yudream.base.interfaces.system.dashboard.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class DashboardGridPlacementRequest {
    @Min(0)
    private int x;
    @Min(0)
    private int y;
    @Min(1)
    private int w;
    @Min(1)
    private int h;
}
