package online.yudream.base.domain.system.dashboard.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;
import online.yudream.base.domain.system.dashboard.valobj.DashboardLayoutItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class DashboardLayout extends BaseDomain {

    private DashboardLayoutOwnerType ownerType;

    private Long ownerId;

    private List<DashboardLayoutItem> items = new ArrayList<>();

    public static DashboardLayout create(DashboardLayoutOwnerType ownerType, Long ownerId, List<DashboardLayoutItem> items) {
        DashboardLayout layout = new DashboardLayout();
        layout.ownerType = ownerType;
        layout.ownerId = ownerId;
        layout.replaceItems(items);
        layout.validateOwner();
        return layout;
    }

    public void replaceItems(List<DashboardLayoutItem> items) {
        List<DashboardLayoutItem> next = items == null ? new ArrayList<>() : new ArrayList<>(items);
        Set<String> codes = new HashSet<>();
        for (DashboardLayoutItem item : next) {
            if (!codes.add(item.cardCode())) {
                throw new BizException("首页卡片不能重复：" + item.cardCode());
            }
        }
        this.items = next;
    }

    private void validateOwner() {
        if (ownerType == null) {
            throw new BizException("首页布局归属不能为空");
        }
        if (ownerType == DashboardLayoutOwnerType.USER && ownerId == null) {
            throw new BizException("用户首页布局必须绑定用户");
        }
        if (ownerType == DashboardLayoutOwnerType.DEFAULT) {
            ownerId = null;
        }
    }
}
