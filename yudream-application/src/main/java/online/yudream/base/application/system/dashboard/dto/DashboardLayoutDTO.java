package online.yudream.base.application.system.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;
import online.yudream.base.domain.system.dashboard.valobj.DashboardLayoutItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutDTO {
    private Long id;
    private DashboardLayoutOwnerType ownerType;
    private Long ownerId;
    @Builder.Default
    private List<DashboardLayoutItem> items = new ArrayList<>();
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
