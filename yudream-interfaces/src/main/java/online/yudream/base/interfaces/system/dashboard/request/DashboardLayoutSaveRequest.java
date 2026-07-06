package online.yudream.base.interfaces.system.dashboard.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardLayoutSaveRequest {
    @Valid
    private List<DashboardLayoutItemRequest> items = new ArrayList<>();
}
