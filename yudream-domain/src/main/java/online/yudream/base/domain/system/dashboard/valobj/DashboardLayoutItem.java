package online.yudream.base.domain.system.dashboard.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.HashMap;
import java.util.Map;

public record DashboardLayoutItem(
        String cardCode,
        boolean visible,
        Map<String, DashboardGridPlacement> placements
) {
    public DashboardLayoutItem {
        if (isBlank(cardCode)) {
            throw new BizException("卡片编码不能为空");
        }
        cardCode = cardCode.trim();
        placements = placements == null ? new HashMap<>() : new HashMap<>(placements);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
