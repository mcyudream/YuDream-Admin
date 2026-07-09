package online.yudream.base.interfaces.system.menu.assembler;

import online.yudream.base.application.system.menu.cmd.MenuCreateCmd;
import online.yudream.base.application.system.menu.cmd.MenuUpdateCmd;
import online.yudream.base.application.system.menu.dto.MenuManageDTO;
import online.yudream.base.interfaces.system.menu.request.MenuCreateRequest;
import online.yudream.base.interfaces.system.menu.request.MenuUpdateRequest;
import online.yudream.base.interfaces.system.menu.res.MenuManageRes;

import java.util.List;

public class MenuWebAssembler {

    private MenuWebAssembler() {
    }

    public static MenuCreateCmd toCmd(MenuCreateRequest request) {
        MenuCreateCmd cmd = new MenuCreateCmd();
        cmd.setCode(request.getCode());
        cmd.setName(request.getName());
        cmd.setType(request.getType());
        cmd.setParentCode(request.getParentCode());
        cmd.setModule(request.getModule());
        cmd.setIcon(request.getIcon());
        cmd.setPath(request.getPath());
        cmd.setComponent(request.getComponent());
        cmd.setLink(request.getLink());
        cmd.setSort(request.getSort());
        cmd.setVisible(request.getVisible());
        cmd.setPermission(request.getPermission());
        return cmd;
    }

    public static MenuUpdateCmd toCmd(String code, MenuUpdateRequest request) {
        MenuUpdateCmd cmd = new MenuUpdateCmd();
        cmd.setCode(code);
        cmd.setName(request.getName());
        cmd.setType(request.getType());
        cmd.setParentCode(request.getParentCode());
        cmd.setModule(request.getModule());
        cmd.setIcon(request.getIcon());
        cmd.setPath(request.getPath());
        cmd.setComponent(request.getComponent());
        cmd.setLink(request.getLink());
        cmd.setSort(request.getSort());
        cmd.setVisible(request.getVisible());
        cmd.setPermission(request.getPermission());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static List<MenuManageRes> toResList(List<MenuManageDTO> items) {
        return items == null ? List.of() : items.stream().map(MenuWebAssembler::toRes).toList();
    }

    public static MenuManageRes toRes(MenuManageDTO dto) {
        return MenuManageRes.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .type(dto.getType())
                .parentCode(dto.getParentCode())
                .module(dto.getModule())
                .icon(dto.getIcon())
                .path(dto.getPath())
                .component(dto.getComponent())
                .link(dto.getLink())
                .sort(dto.getSort())
                .visible(dto.getVisible())
                .permission(dto.getPermission())
                .status(dto.getStatus())
                .source(dto.getSource())
                .pluginCode(dto.getPluginCode())
                .pluginModuleName(dto.getPluginModuleName())
                .runtimeAvailable(dto.getRuntimeAvailable())
                .children(toResList(dto.getChildren()))
                .build();
    }
}
