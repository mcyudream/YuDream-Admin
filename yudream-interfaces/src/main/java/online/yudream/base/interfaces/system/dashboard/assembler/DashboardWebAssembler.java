package online.yudream.base.interfaces.system.dashboard.assembler;

import online.yudream.base.application.system.dashboard.cmd.DashboardLayoutSaveCmd;
import online.yudream.base.application.system.dashboard.dto.DashboardCardDTO;
import online.yudream.base.application.system.dashboard.dto.DashboardLayoutDTO;
import online.yudream.base.application.system.dashboard.dto.DashboardWorkspaceDTO;
import online.yudream.base.domain.system.dashboard.valobj.DashboardGridPlacement;
import online.yudream.base.domain.system.dashboard.valobj.DashboardLayoutItem;
import online.yudream.base.interfaces.system.dashboard.request.DashboardGridPlacementRequest;
import online.yudream.base.interfaces.system.dashboard.request.DashboardLayoutItemRequest;
import online.yudream.base.interfaces.system.dashboard.request.DashboardLayoutSaveRequest;
import online.yudream.base.interfaces.system.dashboard.res.DashboardCardRes;
import online.yudream.base.interfaces.system.dashboard.res.DashboardGridPlacementRes;
import online.yudream.base.interfaces.system.dashboard.res.DashboardLayoutItemRes;
import online.yudream.base.interfaces.system.dashboard.res.DashboardLayoutRes;
import online.yudream.base.interfaces.system.dashboard.res.DashboardWorkspaceRes;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class DashboardWebAssembler {

    private DashboardWebAssembler() {
    }

    public static DashboardLayoutSaveCmd toCmd(DashboardLayoutSaveRequest request) {
        return DashboardLayoutSaveCmd.builder()
                .items(request.getItems() == null
                        ? new java.util.ArrayList<>()
                        : request.getItems().stream().map(DashboardWebAssembler::toItem).toList())
                .build();
    }

    public static DashboardWorkspaceRes toRes(DashboardWorkspaceDTO dto) {
        return DashboardWorkspaceRes.builder()
                .cards(dto.getCards() == null
                        ? new java.util.ArrayList<>()
                        : dto.getCards().stream().map(DashboardWebAssembler::toRes).toList())
                .defaultLayout(toRes(dto.getDefaultLayout()))
                .userLayout(toRes(dto.getUserLayout()))
                .effectiveLayout(toRes(dto.getEffectiveLayout()))
                .build();
    }

    public static DashboardLayoutRes toRes(DashboardLayoutDTO dto) {
        if (dto == null) {
            return null;
        }
        return DashboardLayoutRes.builder()
                .id(dto.getId())
                .ownerType(dto.getOwnerType())
                .ownerId(dto.getOwnerId())
                .items(dto.getItems() == null
                        ? new java.util.ArrayList<>()
                        : dto.getItems().stream().map(DashboardWebAssembler::toRes).toList())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static DashboardCardRes toRes(DashboardCardDTO dto) {
        return DashboardCardRes.builder()
                .code(dto.getCode())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .icon(dto.getIcon())
                .category(dto.getCategory())
                .source(dto.getSource())
                .pluginCode(dto.getPluginCode())
                .permission(dto.getPermission())
                .component(dto.getComponent())
                .actionPath(dto.getActionPath())
                .dragPayloadTemplate(dto.getDragPayloadTemplate())
                .tone(dto.getTone())
                .defaultW(dto.getDefaultW())
                .defaultH(dto.getDefaultH())
                .minW(dto.getMinW())
                .minH(dto.getMinH())
                .sort(dto.getSort())
                .build();
    }

    private static DashboardLayoutItem toItem(DashboardLayoutItemRequest request) {
        return new DashboardLayoutItem(
                request.getCardCode(),
                request.isVisible(),
                request.getPlacements() == null ? new HashMap<>() : request.getPlacements().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toPlacement(entry.getValue())))
        );
    }

    private static DashboardGridPlacement toPlacement(DashboardGridPlacementRequest request) {
        return new DashboardGridPlacement(request.getX(), request.getY(), request.getW(), request.getH());
    }

    private static DashboardLayoutItemRes toRes(DashboardLayoutItem item) {
        return DashboardLayoutItemRes.builder()
                .cardCode(item.cardCode())
                .visible(item.visible())
                .placements(item.placements() == null ? new HashMap<>() : item.placements().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> toRes(entry.getValue()))))
                .build();
    }

    private static DashboardGridPlacementRes toRes(DashboardGridPlacement placement) {
        return DashboardGridPlacementRes.builder()
                .x(placement.x())
                .y(placement.y())
                .w(placement.w())
                .h(placement.h())
                .build();
    }
}
