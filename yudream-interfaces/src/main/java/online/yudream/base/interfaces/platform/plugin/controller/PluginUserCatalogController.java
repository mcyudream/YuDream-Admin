package online.yudream.base.interfaces.platform.plugin.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginUserCatalogAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.platform.plugin.res.PluginDeptCatalogRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginRoleCatalogRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginUserCatalogRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/platform/plugins/users")
@RequiredArgsConstructor
public class PluginUserCatalogController {

    private final PluginUserCatalogAppService catalogAppService;

    @GetMapping
    public Result<List<PluginUserCatalogRes>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deptId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        StpUtil.checkLogin();
        return Result.ok(PluginWebAssembler.toUserCatalogResList(catalogAppService.searchUsers(keyword, deptId, page, size)));
    }

    @GetMapping("/resolve")
    public Result<List<PluginUserCatalogRes>> resolve(@RequestParam(required = false) String ids) {
        StpUtil.checkLogin();
        List<String> values = ids == null || ids.isBlank()
                ? List.of()
                : Arrays.stream(ids.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
        return Result.ok(PluginWebAssembler.toUserCatalogResList(catalogAppService.resolveUsers(values)));
    }

    @GetMapping("/departments")
    public Result<List<PluginDeptCatalogRes>> departments(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean flatten
    ) {
        StpUtil.checkLogin();
        return Result.ok(PluginWebAssembler.toDeptCatalogResList(
                flatten ? catalogAppService.flattenDepartments(keyword) : catalogAppService.departments(keyword)));
    }

    @GetMapping("/roles")
    public Result<List<PluginRoleCatalogRes>> roles() {
        StpUtil.checkLogin();
        return Result.ok(PluginWebAssembler.toRoleCatalogResList(catalogAppService.roles()));
    }
}
