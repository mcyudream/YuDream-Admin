package online.yudream.base.interfaces.system.dashboard.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutRes {
    private Long id;
    private DashboardLayoutOwnerType ownerType;
    private Long ownerId;
    @Builder.Default
    private List<DashboardLayoutItemRes> items = new ArrayList<>();
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
