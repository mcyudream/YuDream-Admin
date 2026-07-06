package online.yudream.base.infra.system.dashboard.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutItemDO {
    private String cardCode;
    private boolean visible;
    private Map<String, DashboardGridPlacementDO> placements = new HashMap<>();
}
