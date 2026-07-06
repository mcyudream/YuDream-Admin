package online.yudream.base.application.system.dashboard.assembler;

import online.yudream.base.application.system.dashboard.dto.DashboardCardDTO;
import online.yudream.base.application.system.dashboard.dto.DashboardLayoutDTO;
import online.yudream.base.domain.system.dashboard.aggregate.DashboardLayout;
import online.yudream.base.domain.system.dashboard.valobj.DashboardCardDefinition;

import java.util.ArrayList;

public final class DashboardAssembler {

    private DashboardAssembler() {
    }

    public static DashboardCardDTO toDTO(DashboardCardDefinition card) {
        return DashboardCardDTO.builder()
                .code(card.code())
                .title(card.title())
                .description(card.description())
                .icon(card.icon())
                .category(card.category())
                .source(card.source())
                .pluginCode(card.pluginCode())
                .permission(card.permission())
                .component(card.component())
                .actionPath(card.actionPath())
                .dragPayloadTemplate(card.dragPayloadTemplate())
                .tone(card.tone())
                .defaultW(card.defaultW())
                .defaultH(card.defaultH())
                .minW(card.minW())
                .minH(card.minH())
                .sort(card.sort())
                .build();
    }

    public static DashboardLayoutDTO toDTO(DashboardLayout layout) {
        if (layout == null) {
            return null;
        }
        return DashboardLayoutDTO.builder()
                .id(layout.getId())
                .ownerType(layout.getOwnerType())
                .ownerId(layout.getOwnerId())
                .items(layout.getItems() == null ? new ArrayList<>() : new ArrayList<>(layout.getItems()))
                .createTime(layout.getCreateTime())
                .updateTime(layout.getUpdateTime())
                .build();
    }
}
