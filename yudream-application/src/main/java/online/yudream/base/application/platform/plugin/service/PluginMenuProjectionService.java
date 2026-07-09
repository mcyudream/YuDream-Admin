package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PluginMenuProjectionService {

    private final MenuRepo menuRepo;

    @Transactional
    public void project(String pluginCode, List<PluginFrontendModuleInfo> modules) {
        requireText(pluginCode, "pluginCode");
        Set<String> currentRegistrationKeys = new HashSet<>();

        for (PluginFrontendModuleInfo module : modules == null ? List.<PluginFrontendModuleInfo>of() : modules) {
            String moduleName = requireText(module.moduleName(), "moduleName");
            String moduleKey = "module:" + moduleName;
            registerKey(currentRegistrationKeys, moduleKey);
            Menu moduleMenu = sync(pluginCode, moduleName, moduleKey, moduleDefaults(pluginCode, module, moduleKey));

            Map<String, Menu> parentMenus = new HashMap<>();
            for (PluginFrontendRouteInfo route : module.routes()) {
                String defaultParentCode = moduleMenu.getCode();
                if (StringUtils.hasText(route.parentPath())) {
                    Menu parentMenu = parentMenus.computeIfAbsent(route.parentPath(), parentPath -> {
                        String parentKey = "parent:" + moduleName + ":" + parentPath;
                        registerKey(currentRegistrationKeys, parentKey);
                        return sync(
                                pluginCode,
                                moduleName,
                                parentKey,
                                parentDefaults(pluginCode, moduleName, parentKey, moduleMenu.getCode(), route)
                        );
                    });
                    defaultParentCode = parentMenu.getCode();
                }

                String routeName = requireText(route.name(), "routeName");
                String routeKey = "route:" + moduleName + ":" + routeName;
                registerKey(currentRegistrationKeys, routeKey);
                sync(
                        pluginCode,
                        moduleName,
                        routeKey,
                        routeDefaults(pluginCode, moduleName, routeKey, defaultParentCode, route)
                );
            }
        }

        menuRepo.findByPluginCode(pluginCode).stream()
                .filter(menu -> !currentRegistrationKeys.contains(menu.getPluginRegistrationKey()))
                .forEach(menu -> {
                    menu.setRuntimeAvailable(false);
                    menuRepo.save(menu);
                });
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

    private Menu moduleDefaults(String pluginCode,
                                PluginFrontendModuleInfo module,
                                String registrationKey) {
        return Menu.builder()
                .code(menuCode(pluginCode, registrationKey))
                .name(defaultText(module.menuTitle(), module.moduleName()))
                .type(MenuNodeType.CATEGORY)
                .module(module.moduleName())
                .icon(module.menuIcon())
                .sort(defaultSort(module.menuSort()))
                .visible(true)
                .status(MenuStatus.ACTIVE)
                .build();
    }

    private Menu parentDefaults(String pluginCode,
                                String moduleName,
                                String registrationKey,
                                String parentCode,
                                PluginFrontendRouteInfo route) {
        return Menu.builder()
                .code(menuCode(pluginCode, registrationKey))
                .name(defaultText(route.parentTitle(), route.parentPath()))
                .type(MenuNodeType.LAYOUT)
                .parentCode(parentCode)
                .module(moduleName)
                .icon(route.parentIcon())
                .path(route.parentPath())
                .component("Layout")
                .sort(defaultSort(route.parentSort()))
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

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private void registerKey(Set<String> keys, String registrationKey) {
        if (!keys.add(registrationKey)) {
            throw new IllegalArgumentException("Duplicate plugin menu registration key: " + registrationKey);
        }
    }
}
