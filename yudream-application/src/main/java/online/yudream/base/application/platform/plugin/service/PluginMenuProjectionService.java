package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteSortSetting;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendSortSetting;
import online.yudream.base.domain.platform.plugin.valobj.PluginMenuOverrideInfo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PluginMenuProjectionService {

    private final MenuRepo menuRepo;

    public void project(String pluginCode, List<PluginFrontendModuleInfo> modules) {
        ProjectionPlan plan = validate(pluginCode, modules);

        for (ModulePlan modulePlan : plan.modules()) {
            Menu moduleMenu = sync(
                    pluginCode,
                    modulePlan.moduleName(),
                    modulePlan.registrationKey(),
                    moduleDefaults(pluginCode, modulePlan)
            );

            Map<String, Menu> parentMenus = new HashMap<>();
            for (RoutePlan routePlan : modulePlan.routes()) {
                String defaultParentCode = moduleMenu.getCode();
                if (routePlan.parentPath() != null) {
                    Menu parentMenu = parentMenus.computeIfAbsent(routePlan.parentPath(), parentPath -> {
                        ParentDeclaration parent = modulePlan.parents().get(parentPath);
                        return sync(
                                pluginCode,
                                modulePlan.moduleName(),
                                parent.registrationKey(),
                                parentDefaults(pluginCode, modulePlan.moduleName(), moduleMenu.getCode(), parent)
                        );
                    });
                    defaultParentCode = parentMenu.getCode();
                }

                sync(
                        pluginCode,
                        modulePlan.moduleName(),
                        routePlan.registrationKey(),
                        routeDefaults(
                                pluginCode,
                                modulePlan.moduleName(),
                                routePlan.registrationKey(),
                                defaultParentCode,
                                routePlan.route()
                        )
                );
            }
        }

        menuRepo.findByPluginCode(pluginCode).stream()
                .filter(menu -> !plan.registrationKeys().contains(menu.getPluginRegistrationKey()))
                .forEach(menu -> {
                    menu.setRuntimeAvailable(false);
                    menuRepo.save(menu);
                });
    }

    public void markUnavailable(String pluginCode) {
        menuRepo.findByPluginCode(pluginCode).forEach(menu -> {
            if (Boolean.FALSE.equals(menu.getRuntimeAvailable())) {
                return;
            }
            Boolean previous = menu.getRuntimeAvailable();
            menu.setRuntimeAvailable(false);
            try {
                menuRepo.save(menu);
            } catch (RuntimeException e) {
                menu.setRuntimeAvailable(previous);
                throw e;
            }
        });
    }

    public void markUnavailableExcept(Set<String> runtimeEnabledPluginCodes) {
        Set<String> enabledCodes = runtimeEnabledPluginCodes == null ? Set.of() : runtimeEnabledPluginCodes;
        menuRepo.findAll().stream()
                .filter(Menu::isPluginMenu)
                .map(Menu::getPluginCode)
                .filter(StringUtils::hasText)
                .filter(pluginCode -> !enabledCodes.contains(pluginCode))
                .distinct()
                .forEach(this::markUnavailable);
    }

    public void updateSorts(String pluginCode,
                            PluginFrontendModuleInfo module,
                            PluginFrontendSortSetting setting) {
        requireText(pluginCode, "插件编码");
        if (module == null || setting == null || !Objects.equals(module.moduleName(), setting.moduleName())) {
            throw new BizException("插件前端排序配置与模块不匹配");
        }
        validateSortConflicts(module, setting);

        List<SortUpdate> updates = new ArrayList<>();
        Menu moduleMenu = requireAvailableProjection(pluginCode, "module:" + module.moduleName());
        addSortUpdate(updates, moduleMenu, module.menuSort());

        Map<String, Integer> routeSorts = new LinkedHashMap<>();
        Map<String, Integer> parentSorts = new LinkedHashMap<>();
        for (PluginFrontendRouteInfo route : module.routes()) {
            String routeKey = "route:" + module.moduleName() + ":"
                    + requireText(route.name(), "插件前端路由名称");
            putConsistentSort(routeSorts, routeKey, route.sort(), "插件菜单路由排序冲突: ");

            String parentPath = normalizeOptionalText(route.parentPath());
            if (parentPath != null) {
                String parentKey = "parent:" + module.moduleName() + ":" + parentPath;
                putConsistentSort(parentSorts, parentKey, route.parentSort(), "插件菜单父目录排序冲突: ");
            }
        }
        routeSorts.forEach((registrationKey, sort) ->
                addSortUpdate(updates, requireAvailableProjection(pluginCode, registrationKey), sort));
        parentSorts.forEach((registrationKey, sort) ->
                addSortUpdate(updates, requireAvailableProjection(pluginCode, registrationKey), sort));

        updates.forEach(update -> {
            if (!Objects.equals(update.menu().getSort(), update.sort())) {
                update.menu().setSort(update.sort());
                menuRepo.save(update.menu());
            }
        });
    }

    private void validateSortConflicts(PluginFrontendModuleInfo module, PluginFrontendSortSetting setting) {
        Map<String, Integer> routeSorts = new LinkedHashMap<>();
        Map<String, Integer> parentSorts = new LinkedHashMap<>();
        for (PluginFrontendRouteSortSetting routeSetting : setting.routes()) {
            PluginFrontendRouteInfo route = module.routes().stream()
                    .filter(routeSetting::matches)
                    .findFirst()
                    .orElseThrow(() -> new BizException("插件菜单路由不存在"));
            String routeKey = "route:" + module.moduleName() + ":"
                    + requireText(route.name(), "插件前端路由名称");
            putConsistentSort(routeSorts, routeKey, routeSetting.sort(), "插件菜单路由排序冲突: ");

            String parentPath = normalizeOptionalText(route.parentPath());
            if (parentPath != null) {
                String parentKey = "parent:" + module.moduleName() + ":" + parentPath;
                putConsistentSort(parentSorts, parentKey, routeSetting.parentSort(), "插件菜单父目录排序冲突: ");
            }
        }
    }

    private Menu requireAvailableProjection(String pluginCode, String registrationKey) {
        Menu menu = menuRepo.findByPluginCodeAndRegistrationKey(pluginCode, registrationKey)
                .orElseThrow(() -> new BizException("插件菜单投影不存在: " + registrationKey));
        if (!menu.isAvailableForRuntime() || menu.getStatus() == MenuStatus.DISABLED) {
            throw new BizException("插件菜单投影不可用: " + registrationKey);
        }
        return menu;
    }

    private void addSortUpdate(List<SortUpdate> updates, Menu menu, Integer sort) {
        if (sort != null) {
            updates.add(new SortUpdate(menu, sort));
        }
    }

    private void putConsistentSort(Map<String, Integer> sorts,
                                   String registrationKey,
                                   Integer sort,
                                   String conflictMessage) {
        if (sort == null) {
            return;
        }
        Integer existing = sorts.putIfAbsent(registrationKey, sort);
        if (existing != null && !Objects.equals(existing, sort)) {
            throw new BizException(conflictMessage + registrationKey);
        }
    }

    public List<PluginFrontendModuleInfo> applyOverrides(List<PluginFrontendModuleInfo> modules) {
        List<PluginFrontendModuleInfo> overridden = new ArrayList<>();
        for (PluginFrontendModuleInfo module : modules == null ? List.<PluginFrontendModuleInfo>of() : modules) {
            Map<String, Menu> menus = menuRepo.findByPluginCode(module.pluginCode()).stream()
                    .filter(menu -> StringUtils.hasText(menu.getPluginRegistrationKey()))
                    .collect(Collectors.toMap(
                            Menu::getPluginRegistrationKey,
                            menu -> menu,
                            (left, right) -> left
                    ));
            Menu moduleMenu = menus.get("module:" + module.moduleName());
            if (!enabled(moduleMenu)) {
                continue;
            }

            List<PluginFrontendRouteInfo> routes = module.routes().stream()
                    .map(route -> applyRouteOverride(module, route, menus))
                    .filter(Objects::nonNull)
                    .toList();
            overridden.add(overriddenModule(module, moduleMenu, routes));
        }
        return List.copyOf(overridden);
    }

    private PluginFrontendModuleInfo overriddenModule(PluginFrontendModuleInfo module,
                                                       Menu moduleMenu,
                                                       List<PluginFrontendRouteInfo> routes) {
        return PluginFrontendModuleInfo.withMenuOverride(module, menuOverride(moduleMenu), routes);
    }

    private PluginFrontendRouteInfo applyRouteOverride(PluginFrontendModuleInfo module,
                                                        PluginFrontendRouteInfo route,
                                                        Map<String, Menu> menus) {
        Menu routeMenu = menus.get("route:" + module.moduleName() + ":" + route.name());
        if (!enabled(routeMenu)) {
            return null;
        }

        Menu parentMenu = menus.values().stream()
                .filter(menu -> Objects.equals(routeMenu.getParentCode(), menu.getCode()))
                .filter(menu -> menu.getPluginRegistrationKey().startsWith("parent:"))
                .findFirst()
                .orElse(null);
        if (parentMenu != null && !enabled(parentMenu)) {
            return null;
        }

        return overriddenRoute(route, routeMenu, parentMenu);
    }

    private PluginFrontendRouteInfo overriddenRoute(PluginFrontendRouteInfo route,
                                                      Menu routeMenu,
                                                      Menu parentMenu) {
        return PluginFrontendRouteInfo.withMenuOverrides(
                route,
                menuOverride(routeMenu),
                parentMenu == null ? null : menuOverride(parentMenu)
        );
    }

    private PluginMenuOverrideInfo menuOverride(Menu menu) {
        return PluginMenuOverrideInfo.builder()
                .code(menu.getCode())
                .name(menu.getName())
                .type(menu.getType())
                .parentCode(menu.getParentCode())
                .module(menu.getModule())
                .icon(menu.getIcon())
                .path(menu.getPath())
                .component(menu.getComponent())
                .link(menu.getLink())
                .sort(menu.getSort())
                .visible(menu.getVisible())
                .permission(menu.getPermission())
                .status(menu.getStatus())
                .build();
    }

    private boolean enabled(Menu menu) {
        return menu != null
                && menu.isAvailableForRuntime()
                && menu.getStatus() != MenuStatus.DISABLED;
    }

    private ProjectionPlan validate(String pluginCode, List<PluginFrontendModuleInfo> modules) {
        requireText(pluginCode, "插件编码");
        Set<String> registrationKeys = new HashSet<>();
        List<ModulePlan> modulePlans = new ArrayList<>();

        for (PluginFrontendModuleInfo module : modules == null ? List.<PluginFrontendModuleInfo>of() : modules) {
            if (module == null) {
                throw new BizException("插件前端模块声明不能为空");
            }
            String moduleName = requireText(module.moduleName(), "插件前端模块名称");
            String moduleKey = "module:" + moduleName;
            registerKey(registrationKeys, moduleKey);

            Map<String, ParentDeclaration> parents = new LinkedHashMap<>();
            List<RoutePlan> routePlans = new ArrayList<>();
            for (PluginFrontendRouteInfo route : module.routes()) {
                if (route == null) {
                    throw new BizException("插件前端路由声明不能为空");
                }
                String parentPath = normalizeOptionalText(route.parentPath());
                if (parentPath != null) {
                    String parentKey = "parent:" + moduleName + ":" + parentPath;
                    ParentDeclaration candidate = new ParentDeclaration(
                            parentKey,
                            parentPath,
                            normalizeOptionalText(route.parentTitle()),
                            normalizeOptionalText(route.parentIcon()),
                            defaultSort(route.parentSort())
                    );
                    ParentDeclaration existing = parents.putIfAbsent(parentPath, candidate);
                    if (existing == null) {
                        registerKey(registrationKeys, parentKey);
                    } else if (!existing.equals(candidate)) {
                        throw new BizException("插件菜单父目录声明冲突: " + parentKey);
                    }
                }

                String routeName = requireText(route.name(), "插件前端路由名称");
                String routeKey = "route:" + moduleName + ":" + routeName;
                registerKey(registrationKeys, routeKey);
                routePlans.add(new RoutePlan(routeKey, parentPath, route));
            }
            modulePlans.add(new ModulePlan(moduleName, moduleKey, module, Map.copyOf(parents), List.copyOf(routePlans)));
        }
        return new ProjectionPlan(Set.copyOf(registrationKeys), List.copyOf(modulePlans));
    }

    private Menu sync(String pluginCode,
                      String moduleName,
                      String registrationKey,
                      Menu defaults) {
        Menu menu = menuRepo.findByPluginCodeAndRegistrationKey(pluginCode, registrationKey)
                .orElse(defaults);
        menu.setSource(MenuSource.PLUGIN);
        menu.setPluginCode(pluginCode);
        menu.setPluginModuleName(moduleName);
        menu.setPluginRegistrationKey(registrationKey);
        menu.setRuntimeAvailable(true);
        return menuRepo.save(menu);
    }

    private Menu moduleDefaults(String pluginCode, ModulePlan plan) {
        PluginFrontendModuleInfo module = plan.module();
        return Menu.builder()
                .code(menuCode(pluginCode, plan.registrationKey()))
                .name(defaultText(module.menuTitle(), plan.moduleName()))
                .type(MenuNodeType.CATEGORY)
                .parentCode(blankToNull(module.parentCode()))
                .module(plan.moduleName())
                .icon(module.menuIcon())
                .sort(defaultSort(module.menuSort()))
                .visible(true)
                .status(MenuStatus.ACTIVE)
                .build();
    }

    private Menu parentDefaults(String pluginCode,
                                String moduleName,
                                String parentCode,
                                ParentDeclaration parent) {
        return Menu.builder()
                .code(menuCode(pluginCode, parent.registrationKey()))
                .name(defaultText(parent.title(), parent.path()))
                .type(MenuNodeType.LAYOUT)
                .parentCode(parentCode)
                .module(moduleName)
                .icon(parent.icon())
                .path(parent.path())
                .component("Layout")
                .sort(parent.sort())
                .visible(true)
                .status(MenuStatus.ACTIVE)
                .build();
    }

    private Menu routeDefaults(String pluginCode,
                               String moduleName,
                               String registrationKey,
                               String parentCode,
                               PluginFrontendRouteInfo route) {
        return Menu.builder()
                .code(menuCode(pluginCode, registrationKey))
                .name(defaultText(route.title(), route.name()))
                .type(MenuNodeType.MENU)
                .parentCode(parentCode)
                .module(moduleName)
                .icon(route.icon())
                .path(route.path())
                .component(route.component())
                .sort(defaultSort(route.sort()))
                .visible(true)
                .permission(route.permission())
                .status(MenuStatus.ACTIVE)
                .build();
    }

    private String menuCode(String pluginCode, String registrationKey) {
        return "plugin:" + pluginCode + ":" + registrationKey;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private int defaultSort(Integer sort) {
        return sort == null ? 0 : sort;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(field + "不能为空");
        }
        return value.trim();
    }

    private void registerKey(Set<String> keys, String registrationKey) {
        if (!keys.add(registrationKey)) {
            throw new BizException("插件菜单注册标识重复: " + registrationKey);
        }
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ProjectionPlan(Set<String> registrationKeys, List<ModulePlan> modules) {
    }

    private record ModulePlan(String moduleName,
                              String registrationKey,
                              PluginFrontendModuleInfo module,
                              Map<String, ParentDeclaration> parents,
                              List<RoutePlan> routes) {
    }

    private record ParentDeclaration(String registrationKey,
                                     String path,
                                     String title,
                                     String icon,
                                     int sort) {
    }

    private record RoutePlan(String registrationKey,
                             String parentPath,
                             PluginFrontendRouteInfo route) {
    }

    private record SortUpdate(Menu menu, Integer sort) {
    }
}
