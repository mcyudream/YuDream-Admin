package online.yudream.base.interfaces.system.dashboard.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardWorkspaceRes {
    @Builder.Default
    private List<DashboardCardRes> cards = new ArrayList<>();
    private DashboardLayoutRes defaultLayout;
    private DashboardLayoutRes userLayout;
    private DashboardLayoutRes effectiveLayout;
}
