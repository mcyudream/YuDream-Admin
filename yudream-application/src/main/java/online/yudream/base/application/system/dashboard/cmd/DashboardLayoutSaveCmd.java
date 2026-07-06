package online.yudream.base.application.system.dashboard.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.dashboard.valobj.DashboardLayoutItem;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutSaveCmd {
    @Builder.Default
    private List<DashboardLayoutItem> items = new ArrayList<>();
}
