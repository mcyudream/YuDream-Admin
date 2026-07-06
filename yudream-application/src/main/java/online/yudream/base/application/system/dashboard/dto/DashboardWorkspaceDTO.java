package online.yudream.base.application.system.dashboard.dto;

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
public class DashboardWorkspaceDTO {
    @Builder.Default
    private List<DashboardCardDTO> cards = new ArrayList<>();
    private DashboardLayoutDTO defaultLayout;
    private DashboardLayoutDTO userLayout;
    private DashboardLayoutDTO effectiveLayout;
}
