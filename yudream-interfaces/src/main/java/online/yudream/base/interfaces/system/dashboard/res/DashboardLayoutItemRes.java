package online.yudream.base.interfaces.system.dashboard.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutItemRes {
    private String cardCode;
    private boolean visible;
    @Builder.Default
    private Map<String, DashboardGridPlacementRes> placements = new HashMap<>();
}
