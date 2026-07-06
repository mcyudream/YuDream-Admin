package online.yudream.base.interfaces.system.dashboard.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class DashboardLayoutItemRequest {
    @NotBlank(message = "卡片编码不能为空")
    private String cardCode;
    private boolean visible = true;
    @Valid
    private Map<String, DashboardGridPlacementRequest> placements = new HashMap<>();
}
