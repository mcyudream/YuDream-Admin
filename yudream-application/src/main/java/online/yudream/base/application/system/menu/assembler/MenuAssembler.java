package online.yudream.base.application.system.menu.assembler;

import online.yudream.base.application.system.menu.dto.MenuCandidateDTO;
import online.yudream.base.domain.system.menu.aggregate.Menu;

public final class MenuAssembler {

    private MenuAssembler() {
    }

    public static MenuCandidateDTO toCandidateDTO(Menu menu) {
        return MenuCandidateDTO.builder()
                .code(menu.getCode())
                .name(menu.getName())
                .type(menu.getType())
                .parentCode(menu.getParentCode())
                .module(menu.getModule())
                .icon(menu.getIcon())
                .sort(menu.getSort())
                .source(menu.getSource())
                .pluginCode(menu.getPluginCode())
                .pluginModuleName(menu.getPluginModuleName())
                .build();
    }
}
