package online.yudream.base.interfaces.system.dashboard.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardGridPlacementRes {
    private int x;
    private int y;
    private int w;
    private int h;
}
