package online.yudream.base.interfaces.system.menu.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.interfaces.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 菜单控制器。
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuAppService menuAppService;
    private final PermissionAppService permissionAppService;

    /**
     * 获取当前用户可见的菜单路由树。
     */
    @GetMapping("/routes")
    public Result<List<Map<String, Object>>> routes() {
        List<String> permissions = permissionAppService.getUserPermissions(StpUtil.getLoginIdAsLong());
        return Result.ok(menuAppService.buildRouteTree(permissions));
    }
}
