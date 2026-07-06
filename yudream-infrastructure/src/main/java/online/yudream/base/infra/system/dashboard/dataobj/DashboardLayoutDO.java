package online.yudream.base.infra.system.dashboard.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysDashboardLayout")
@CompoundIndex(name = "idx_dashboard_layout_owner", def = "{'ownerType': 1, 'ownerId': 1}", unique = true)
public class DashboardLayoutDO extends BaseDO {
    private DashboardLayoutOwnerType ownerType;
    private Long ownerId;
    private List<DashboardLayoutItemDO> items = new ArrayList<>();
}
