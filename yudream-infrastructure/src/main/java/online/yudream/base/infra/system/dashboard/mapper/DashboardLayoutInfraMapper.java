package online.yudream.base.infra.system.dashboard.mapper;

import online.yudream.base.domain.system.dashboard.aggregate.DashboardLayout;
import online.yudream.base.domain.system.dashboard.valobj.DashboardGridPlacement;
import online.yudream.base.domain.system.dashboard.valobj.DashboardLayoutItem;
import online.yudream.base.infra.system.dashboard.dataobj.DashboardGridPlacementDO;
import online.yudream.base.infra.system.dashboard.dataobj.DashboardLayoutDO;
import online.yudream.base.infra.system.dashboard.dataobj.DashboardLayoutItemDO;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class DashboardLayoutInfraMapper {

    private DashboardLayoutInfraMapper() {
    }

    public static DashboardLayoutDO toDataObj(DashboardLayout layout) {
        if (layout == null) {
            return null;
        }
        DashboardLayoutDO dataObj = new DashboardLayoutDO();
        dataObj.setId(layout.getId());
        dataObj.setVersion(layout.getVersion());
        dataObj.setCreateTime(layout.getCreateTime());
        dataObj.setUpdateTime(layout.getUpdateTime());
        dataObj.setOwnerType(layout.getOwnerType());
        dataObj.setOwnerId(layout.getOwnerId());
        dataObj.setItems(layout.getItems() == null
                ? new java.util.ArrayList<>()
                : layout.getItems().stream().map(DashboardLayoutInfraMapper::toDataObj).toList());
        return dataObj;
    }

    public static DashboardLayout toDomain(DashboardLayoutDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        DashboardLayout layout = DashboardLayout.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .ownerType(dataObj.getOwnerType())
                .ownerId(dataObj.getOwnerId())
                .items(dataObj.getItems() == null
                        ? new java.util.ArrayList<>()
                        : dataObj.getItems().stream().map(DashboardLayoutInfraMapper::toDomain).toList())
                .build();
        return layout;
    }

    private static DashboardLayoutItemDO toDataObj(DashboardLayoutItem item) {
        return new DashboardLayoutItemDO(
                item.cardCode(),
                item.visible(),
                item.placements() == null ? new HashMap<>() : item.placements().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toDataObj(entry.getValue())))
        );
    }

    private static DashboardLayoutItem toDomain(DashboardLayoutItemDO dataObj) {
        return new DashboardLayoutItem(
                dataObj.getCardCode(),
                dataObj.isVisible(),
                dataObj.getPlacements() == null ? new HashMap<>() : dataObj.getPlacements().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toDomain(entry.getValue())))
        );
    }

    private static DashboardGridPlacementDO toDataObj(DashboardGridPlacement placement) {
        return new DashboardGridPlacementDO(placement.x(), placement.y(), placement.w(), placement.h());
    }

    private static DashboardGridPlacement toDomain(DashboardGridPlacementDO dataObj) {
        return new DashboardGridPlacement(dataObj.getX(), dataObj.getY(), dataObj.getW(), dataObj.getH());
    }
}
