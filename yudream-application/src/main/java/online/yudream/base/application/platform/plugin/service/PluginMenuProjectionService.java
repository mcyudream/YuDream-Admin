package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PluginMenuProjectionService {

    private final MenuRepo menuRepo;

    public void project(String pluginCode, List<PluginFrontendModuleInfo> modules) {
        ProjectionPlan plan = validate(pluginCode, modules);

        for (ModulePlan modulePlan : plan.modules()) {
            String declaredParentCode = blankToNull(modulePlan.module().parentCode());
            String moduleParentCode;
            if (declaredParentCode == null) {
                Menu moduleMenu = createIfMissing(
                        pluginCode,
                        modulePlan.moduleName(),
                        modulePlan.registrationKey(),
                        moduleDefaults(pluginCode, modulePlan)
                );
                moduleParentCode = moduleMenu.getCode();
            } else {
                moduleParentCode = declaredParentCode;
            }

            Map<String, Menu> parentMenus = new HashMap<>();
            for (RoutePlan routePlan : modulePlan.routes()) {
                String defaultParentCode = moduleParentCode;
                if (routePlan.parentPath() != null) {
                    String parentCode = moduleParentCode;
                    Menu parentMenu = parentMenus.computeIfAbsent(routePlan.parentPath(), parentPath -> {
                        ParentDeclaration parent = modulePlan.parents().get(parentPath);
                        return createIfMissing(
                                pluginCode,
                                modulePlan.moduleName(),
                                parent.registrationKey(),
                                parentDefaults(pluginCode, modulePlan.moduleName(), parentCode, parent)
                        );
                    });
                    defaultParentCode = parentMenu.getCode();
                }

                Menu routeMenu = createIfMissing(
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
                if (routePlan.route().hideInMenu() && routeMenu.isVisibleInMenu()) {
                    routeMenu.setVisible(false);
                    menuRepo.save(routeMenu);
                }
            }
        }
        retireMissingFrontendMenus(pluginCode, declaredRegistrationKeys(plan));
    }

    /**
     * Plugin updates may remove a contextual route or its generated parent.
     * Keep the persisted record for audit/editing, but remove it from runtime navigation.
     */
    private void retireMissingFrontendMenus(String pluginCode, Set<String> declaredKeys) {
        menuRepo.findByPluginCode(pluginCode).forEach(menu -> {
            String registrationKey = menu.getPluginRegistrationKey();
            if (!isFrontendRegistrationKey(registrationKey) || declaredKeys.contains(registrationKey)
                    || Boolean.FALSE.equals(menu.getRuntimeAvailable())) {
                return;
            }
            menu.setRuntimeAvailable(false);
            menuRepo.save(menu);
        });
    }

    private Set<String> declaredRegistrationKeys(ProjectionPlan plan) {
        Set<String> keys = new HashSet<>();
        for (ModulePlan module : plan.modules()) {
            keys.add(module.registrationKey());
            module.parents().values().forEach(parent -> keys.add(parent.registrationKey()));
            module.routes().forEach(route -> keys.add(route.registrationKey()));
        }
        return keys;
    }

    private boolean isFrontendRegistrationKey(String registrationKey) {
        return registrationKey != null && (registrationKey.startsWith("module:")
                || registrationKey.startsWith("parent:") || registrationKey.startsWith("route:"));
    }

    public void restoreAvailable(String pluginCode) {
        menuRepo.findByPluginCode(pluginCode).forEach(menu -> {
            if (!Boolean.TRUE.equals(menu.getRuntimeAvailable())) {
                menu.setRuntimeAvailable(true);
                menuRepo.save(menu);
            }
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
        return new ProjectionPlan(List.copyOf(modulePlans));
    }

    private Menu createIfMissing(String pluginCode,
                                 String moduleName,
                                 String registrationKey,
                                 Menu defaults) {
        return menuRepo.findByCode(defaults.getCode()).orElseGet(() -> {
            defaults.setSource(MenuSource.PLUGIN);
            defaults.setPluginCode(pluginCode);
            defaults.setPluginModuleName(moduleName);
            defaults.setPluginRegistrationKey(registrationKey);
            defaults.setRuntimeAvailable(true);
            try {
                return menuRepo.save(defaults);
            } catch (RuntimeException saveFailure) {
                Menu concurrentlyCreated = menuRepo.findByCode(defaults.getCode()).orElse(null);
                if (concurrentlyCreated != null) {
                    return concurrentlyCreated;
                }
                throw saveFailure;
            }
        });
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
                .visible(!route.hideInMenu())
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

    private record ProjectionPlan(List<ModulePlan> modules) {
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

}
