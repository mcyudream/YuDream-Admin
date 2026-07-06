package online.yudream.base.infra.system.dashboard.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardGridPlacementDO {
    private int x;
    private int y;
    private int w;
    private int h;
}
