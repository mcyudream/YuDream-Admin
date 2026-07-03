package online.yudream.base.interfaces.system.menu.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.menu.query.MenuTreeQuery;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.menu.assembler.MenuWebAssembler;
import online.yudream.base.interfaces.system.menu.request.MenuCreateRequest;
import online.yudream.base.interfaces.system.menu.request.MenuUpdateRequest;
import online.yudream.base.interfaces.system.menu.res.MenuManageRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuManageController {

    private final MenuAppService menuAppService;

    @GetMapping
    public Result<List<MenuManageRes>> tree(MenuTreeQuery query) {
        StpUtil.checkLogin();
        return Result.ok(MenuWebAssembler.toResList(menuAppService.tree(query)));
    }

    @PostMapping
    @PermissionRegister(code = "system:menu:create", name = "新增菜单", module = "系统管理", desc = "新增菜单")
    public Result<MenuManageRes> create(@Valid @RequestBody MenuCreateRequest request) {
        return Result.ok(MenuWebAssembler.toRes(menuAppService.create(MenuWebAssembler.toCmd(request))));
    }

    @PutMapping("/{code}")
    @PermissionRegister(code = "system:menu:edit", name = "编辑菜单", module = "系统管理", desc = "编辑菜单")
    public Result<MenuManageRes> update(@PathVariable String code, @Valid @RequestBody MenuUpdateRequest request) {
        return Result.ok(MenuWebAssembler.toRes(menuAppService.update(MenuWebAssembler.toCmd(code, request))));
    }

    @DeleteMapping("/{code}")
    @PermissionRegister(code = "system:menu:delete", name = "删除菜单", module = "系统管理", desc = "停用菜单")
    public Result<Void> disable(@PathVariable String code) {
        menuAppService.disable(code);
        return Result.ok();
    }
}
