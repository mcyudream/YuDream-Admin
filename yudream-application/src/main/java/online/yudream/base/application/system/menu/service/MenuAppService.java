package online.yudream.base.application.system.menu.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.menu.cmd.MenuCreateCmd;
import online.yudream.base.application.system.menu.cmd.MenuUpdateCmd;
import online.yudream.base.application.system.menu.dto.MenuManageDTO;
import online.yudream.base.application.system.menu.query.MenuTreeQuery;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单应用服务。
 */
@Service
@RequiredArgsConstructor
public class MenuAppService {

    private static final Map<String, String> PLATFORM_MENU_CAPABILITIES = Map.ofEntries(
            Map.entry("platform:wiki", "wiki"),
            Map.entry("platform:milky", "milky"),
            Map.entry("platform:docs", "api-docs"),
            Map.entry("platform:integration", "integration"),
            Map.entry("platform:document", "document-template"),
            Map.entry("platform:graph", "neo4j"),
            Map.entry("platform:form", "form"),
            Map.entry("platform:cms", "cms"),
            Map.entry("platform:agent", "agent"),
            Map.entry("platform:ai:generate", "ai"),
            Map.entry("platform:render:use", "message-render")
    );

    private final MenuDomainService menuDomainService;
    private final MenuRepo menuRepo;
    private final CapabilityModuleRepo capabilityModuleRepo;

    @Transactional(readOnly = true)
    public List<MenuManageDTO> tree(MenuTreeQuery query) {
        List<Menu> allMenus = menuRepo.findAll();
        Map<String, Menu> allMenuMap = allMenus.stream()
                .collect(Collectors.toMap(Menu::getCode, menu -> menu));
        Set<String> selectedCodes = selectManageMenuCodes(allMenus, allMenuMap, query);
        List<MenuManageDTO> nodes = allMenus.stream()
                .filter(menu -> selectedCodes.contains(menu.getCode()))
                .map(menu -> toDTO(menu, resolveIncludedParentCode(menu, allMenuMap, selectedCodes)))
                .toList();
        return buildManageTree(nodes);
    }

    @Transactional
    public MenuManageDTO create(MenuCreateCmd cmd) {
        if (menuRepo.existsByCode(cmd.getCode())) {
            throw new BizException("菜单编码已存在");
        }
        ensureParentExists(cmd.getParentCode());
        Menu menu = Menu.builder()
                .code(cmd.getCode())
                .name(cmd.getName())
                .type(cmd.getType())
                .parentCode(blankToNull(cmd.getParentCode()))
                .module(cmd.getModule())
                .icon(cmd.getIcon())
                .path(resolvePath(cmd.getType(), cmd.getCode(), cmd.getPath()))
                .component(resolveComponent(cmd.getType(), cmd.getComponent()))
                .link(cmd.getLink())
                .sort(cmd.getSort() == null ? 0 : cmd.getSort())
                .visible(cmd.getVisible() == null || cmd.getVisible())
                .permission(cmd.getPermission())
                .status(MenuStatus.ACTIVE)
                .source(MenuSource.SYSTEM)
                .build();
        return toDTO(menuDomainService.syncMenu(menu));
    }

    @Transactional
    public MenuManageDTO update(MenuUpdateCmd cmd) {
        Menu menu = getMenu(cmd.getCode());
        ensureParentExists(cmd.getParentCode());
        ensureNoParentCycle(menu.getCode(), cmd.getParentCode());
        menu.updateBasic(
                cmd.getName(),
                cmd.getType(),
                blankToNull(cmd.getParentCode()),
                cmd.getModule(),
                cmd.getIcon(),
                resolvePath(cmd.getType(), cmd.getCode(), cmd.getPath()),
                resolveComponent(cmd.getType(), cmd.getComponent()),
                cmd.getLink(),
                cmd.getSort(),
                cmd.getVisible() == null || cmd.getVisible(),
                cmd.getPermission()
        );
        if (cmd.getStatus() == MenuStatus.ACTIVE) {
            menu.activate();
        } else if (cmd.getStatus() == MenuStatus.DISABLED) {
            ensureMenuCanDisable(menu.getCode());
            menu.disable();
        }
        return toDTO(menuDomainService.syncMenu(menu));
    }

    @Transactional
    public void disable(String code) {
        Menu menu = getMenu(code);
        ensureMenuCanDisable(code);
        menu.disable();
        menuRepo.save(menu);
    }

    @Transactional
    public void enable(String code) {
        Menu menu = getMenu(code);
        menu.activate();
        menuRepo.save(menu);
    }

    /**
     * 构建当前用户可见的路由树。
     *
     * @param userPermissions 当前用户拥有的权限码集合
     * @return 前端可直接消费的菜单路由结构
     */
    public List<Map<String, Object>> buildRouteTree(Collection<String> userPermissions) {
        Set<String> permissionSet = userPermissions == null ? Collections.emptySet() : new HashSet<>(userPermissions);
        List<Menu> activeMenus = menuDomainService.findActiveMenus();
        Map<String, Menu> activeMenuMap = activeMenus.stream()
                .collect(Collectors.toMap(Menu::getCode, menu -> menu));
        Map<String, Menu> allMenuMap = new HashMap<>(activeMenuMap);
        List<Menu> persistedMenus = menuRepo.findAll();
        if (persistedMenus != null) {
            persistedMenus.forEach(menu -> allMenuMap.putIfAbsent(menu.getCode(), menu));
        }
        List<Menu> allMenus = activeMenus.stream()
                .filter(Menu::isAvailableForRuntime)
                .filter(this::platformCapabilityVisible)
                .toList();
        Set<String> routeMenuCodes = allMenus.stream()
                .map(Menu::getCode)
                .collect(Collectors.toSet());

        Set<String> visibleCodes = allMenus.stream()
                .filter(m -> isVisible(m, permissionSet))
                .map(Menu::getCode)
                .collect(Collectors.toSet());
        if (persistedMenus != null) {
            persistedMenus.stream()
                    .filter(menu -> menu.getType() == MenuNodeType.BUTTON)
                    .filter(menu -> menu.getStatus() == MenuStatus.ACTIVE)
                    .filter(Menu::isAvailableForRuntime)
                    .filter(this::platformCapabilityVisible)
                    .filter(menu -> isVisible(menu, permissionSet))
                    .map(Menu::getCode)
                    .forEach(visibleCodes::add);
        }
        includeStructuralRouteAncestors(visibleCodes, allMenuMap, routeMenuCodes);

        Map<String, List<Menu>> childrenMap = new HashMap<>();
        for (Menu menu : allMenus) {
            String parentCode = resolveRouteParentCode(menu, allMenuMap, routeMenuCodes);
            childrenMap.computeIfAbsent(parentCode, k -> new ArrayList<>()).add(menu);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Menu menu : allMenus) {
            if (resolveRouteParentCode(menu, allMenuMap, routeMenuCodes) != null) {
                continue;
            }
            Map<String, Object> root = menu.getType() == MenuNodeType.CATEGORY
                    ? buildGroup(menu, childrenMap, visibleCodes)
                    : buildRoute(menu, childrenMap, visibleCodes, new HashSet<>());
            if (root != null) {
                result.add(root);
            }
        }

        result.sort((a, b) -> Integer.compare((int) b.getOrDefault("_sort", 0), (int) a.getOrDefault("_sort", 0)));
        result.forEach(m -> m.remove("_sort"));
        return result;
    }

    private Menu getMenu(String code) {
        return menuRepo.findByCode(code).orElseThrow(() -> new BizException("菜单不存在"));
    }

    private boolean matchesQuery(Menu menu, MenuTreeQuery query) {
        if (query == null) {
            return true;
        }
        if (query.getType() != null && menu.getType() != query.getType()) {
            return false;
        }
        if (query.getStatus() != null && menu.getStatus() != query.getStatus()) {
            return false;
        }
        if (!StringUtils.hasText(query.getKeyword())) {
            return true;
        }
        String keyword = query.getKeyword();
        return contains(menu.getCode(), keyword)
                || contains(menu.getName(), keyword)
                || contains(menu.getPermission(), keyword)
                || contains(menu.getPath(), keyword)
                || contains(menu.getComponent(), keyword)
                || contains(menu.getLink(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && value.contains(keyword);
    }

    private void ensureParentExists(String parentCode) {
        if (!StringUtils.hasText(parentCode)) {
            return;
        }
        Menu parent = menuRepo.findByCode(parentCode).orElse(null);
        if (parent == null) {
            throw new BizException("上级菜单不存在");
        }
    }

    private Set<String> selectManageMenuCodes(List<Menu> allMenus,
                                               Map<String, Menu> allMenuMap,
                                               MenuTreeQuery query) {
        Set<String> availableCodes = allMenus.stream()
                .filter(Menu::isAvailableForRuntime)
                .map(Menu::getCode)
                .collect(Collectors.toSet());
        if (!hasManageQueryFilter(query)) {
            return availableCodes;
        }
        Set<String> selectedCodes = allMenus.stream()
                .filter(Menu::isAvailableForRuntime)
                .filter(menu -> matchesQuery(menu, query))
                .map(Menu::getCode)
                .collect(Collectors.toSet());
        for (String matchedCode : List.copyOf(selectedCodes)) {
            includeAvailableAncestors(matchedCode, allMenuMap, availableCodes, selectedCodes);
        }
        return selectedCodes;
    }

    private boolean hasManageQueryFilter(MenuTreeQuery query) {
        return query != null && (query.getType() != null
                || query.getStatus() != null
                || StringUtils.hasText(query.getKeyword()));
    }

    private void includeAvailableAncestors(String code,
                                           Map<String, Menu> allMenuMap,
                                           Set<String> availableCodes,
                                           Set<String> selectedCodes) {
        Menu menu = allMenuMap.get(code);
        String cursor = menu == null ? null : blankToNull(menu.getParentCode());
        Set<String> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            if (availableCodes.contains(cursor)) {
                selectedCodes.add(cursor);
            }
            Menu parent = allMenuMap.get(cursor);
            cursor = parent == null ? null : blankToNull(parent.getParentCode());
        }
    }

    private String resolveIncludedParentCode(Menu menu,
                                             Map<String, Menu> allMenuMap,
                                             Set<String> includedCodes) {
        String cursor = blankToNull(menu.getParentCode());
        Set<String> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            if (includedCodes.contains(cursor)) {
                return cursor;
            }
            Menu parent = allMenuMap.get(cursor);
            cursor = parent == null ? null : blankToNull(parent.getParentCode());
        }
        return null;
    }

    private String resolveRouteParentCode(Menu menu,
                                          Map<String, Menu> allMenuMap,
                                          Set<String> routeMenuCodes) {
        String cursor = blankToNull(menu.getParentCode());
        Set<String> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            Menu parent = allMenuMap.get(cursor);
            if (parent == null) {
                return null;
            }
            if (routeMenuCodes.contains(parent.getCode())) {
                return parent.getCode();
            }
            cursor = blankToNull(parent.getParentCode());
        }
        return null;
    }

    private void includeStructuralRouteAncestors(Set<String> visibleCodes,
                                                   Map<String, Menu> allMenuMap,
                                                   Set<String> routeMenuCodes) {
        for (String code : List.copyOf(visibleCodes)) {
            Menu menu = allMenuMap.get(code);
            String cursor = menu == null ? null : blankToNull(menu.getParentCode());
            Set<String> visited = new HashSet<>();
            while (cursor != null && visited.add(cursor)) {
                Menu parent = allMenuMap.get(cursor);
                if (parent == null) {
                    break;
                }
                if (!routeMenuCodes.contains(cursor)) {
                    cursor = blankToNull(parent.getParentCode());
                    continue;
                }
                visibleCodes.add(cursor);
                cursor = blankToNull(parent.getParentCode());
            }
        }
    }

    private void ensureNoParentCycle(String code, String parentCode) {
        String cursor = blankToNull(parentCode);
        Set<String> visited = new HashSet<>();
        while (cursor != null) {
            if (cursor.equals(code)) {
                throw new BizException("上级菜单不能选择自己或下级菜单");
            }
            if (!visited.add(cursor)) {
                throw new BizException("菜单层级存在循环");
            }
            Menu parent = menuRepo.findByCode(cursor).orElse(null);
            cursor = parent == null ? null : blankToNull(parent.getParentCode());
        }
    }

    private void ensureMenuCanDisable(String code) {
        boolean hasActiveChildren = menuRepo.findAll().stream()
                .anyMatch(menu -> Objects.equals(code, menu.getParentCode()) && menu.getStatus() == MenuStatus.ACTIVE);
        if (hasActiveChildren) {
            throw new BizException("菜单存在启用的子节点，不能停用");
        }
    }

    private List<MenuManageDTO> buildManageTree(List<MenuManageDTO> nodes) {
        Map<String, MenuManageDTO> nodeMap = nodes.stream().collect(Collectors.toMap(MenuManageDTO::getCode, m -> m));
        List<MenuManageDTO> roots = new ArrayList<>();
        for (MenuManageDTO node : nodes) {
            String displayParentCode = node.getDisplayParentCode();
            if (StringUtils.hasText(displayParentCode) && nodeMap.containsKey(displayParentCode)) {
                nodeMap.get(displayParentCode).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        sortManageTree(roots, new HashSet<>());
        return roots;
    }

    private void sortManageTree(List<MenuManageDTO> nodes, Set<String> visited) {
        nodes.sort(Comparator.comparing(MenuManageDTO::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (MenuManageDTO node : nodes) {
            if (visited.add(node.getCode())) {
                sortManageTree(node.getChildren(), visited);
                visited.remove(node.getCode());
            } else {
                node.getChildren().clear();
            }
        }
    }

    private MenuManageDTO toDTO(Menu menu) {
        return toDTO(menu, menu.getParentCode());
    }

    private MenuManageDTO toDTO(Menu menu, String displayParentCode) {
        return MenuManageDTO.builder()
                .code(menu.getCode())
                .name(menu.getName())
                .type(menu.getType())
                .parentCode(menu.getParentCode())
                .displayParentCode(displayParentCode)
                .module(menu.getModule())
                .icon(menu.getIcon())
                .path(menu.getPath())
                .component(menu.getComponent())
                .link(menu.getLink())
                .sort(menu.getSort())
                .visible(menu.getVisible())
                .permission(menu.getPermission())
                .status(menu.getStatus())
                .source(menu.getSource())
                .pluginCode(menu.getPluginCode())
                .pluginModuleName(menu.getPluginModuleName())
                .runtimeAvailable(menu.getRuntimeAvailable())
                .build();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private Map<String, Object> buildGroup(Menu module, Map<String, List<Menu>> childrenMap, Set<String> visibleCodes) {
        if (!visibleCodes.contains(module.getCode())) {
            return null;
        }
        List<Map<String, Object>> children = buildChildren(module.getCode(), childrenMap, visibleCodes, new HashSet<>());
        if (children.isEmpty()) {
            return null;
        }
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("meta", buildMeta(module));
        group.put("children", children);
        group.put("_sort", module.getSort() == null ? 0 : module.getSort());
        return group;
    }

    private List<Map<String, Object>> buildChildren(String parentCode,
                                                    Map<String, List<Menu>> childrenMap,
                                                    Set<String> visibleCodes,
                                                    Set<String> path) {
        List<Menu> children = childrenMap.getOrDefault(parentCode, Collections.emptyList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Menu child : children) {
            Map<String, Object> route = buildRoute(child, childrenMap, visibleCodes, path);
            if (route != null) {
                result.add(route);
            }
        }
        result.sort((a, b) -> Integer.compare((int) b.getOrDefault("_sort", 0), (int) a.getOrDefault("_sort", 0)));
        result.forEach(m -> m.remove("_sort"));
        return result;
    }

    private Map<String, Object> buildRoute(Menu menu,
                                           Map<String, List<Menu>> childrenMap,
                                           Set<String> visibleCodes,
                                           Set<String> path) {
        if (!visibleCodes.contains(menu.getCode()) || menu.getType() == MenuNodeType.BUTTON) {
            return null;
        }
        if (!path.add(menu.getCode())) {
            return null;
        }
        Map<String, Object> route = new LinkedHashMap<>();
        if (menu.getType() == MenuNodeType.MENU
                || menu.getType() == MenuNodeType.LAYOUT
                || menu.getType() == MenuNodeType.LINK) {
            route.put("path", menu.getPath());
            route.put("component", menu.getType() == MenuNodeType.LINK ? "Layout" : menu.getComponent());
            route.put("name", menu.getCode());
        }
        route.put("meta", buildMeta(menu));
        route.put("_sort", menu.getSort() == null ? 0 : menu.getSort());
        List<Map<String, Object>> nested = buildChildren(menu.getCode(), childrenMap, visibleCodes, path);
        if (!nested.isEmpty()) {
            route.put("children", nested);
        }
        path.remove(menu.getCode());
        return route;
    }

    private Map<String, Object> buildMeta(Menu menu) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("menuCode", menu.getCode());
        meta.put("title", menu.getName());
        if (StringUtils.hasText(menu.getIcon())) {
            meta.put("icon", menu.getIcon());
        }
        if (StringUtils.hasText(menu.getLink())) {
            meta.put("link", menu.getLink());
        }
        if (StringUtils.hasText(menu.getPermissionCode())) {
            meta.put("auth", menu.getPermissionCode());
        }
        if (menu.getSort() != null) {
            meta.put("sort", menu.getSort());
        }
        if (!menu.isVisibleInMenu()) {
            meta.put("menu", false);
        }
        if (menu.isPluginMenu()) {
            meta.put("pluginCode", menu.getPluginCode());
            meta.put("pluginModuleName", menu.getPluginModuleName());
        }
        return meta;
    }

    private boolean isVisible(Menu menu, Set<String> userPermissions) {
        String permissionCode = menu.getPermissionCode();
        return userPermissions.contains("*") || userPermissions.contains(permissionCode);
    }

    private boolean platformCapabilityVisible(Menu menu) {
        String capabilityCode = resolvePlatformCapability(menu);
        if (capabilityCode != null) {
            return capabilityModuleRepo.findByCode(capabilityCode)
                    .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                    .orElse(false);
        }
        String inferredCapabilityCode = inferPlatformCapability(menu.getCode());
        if (inferredCapabilityCode == null) {
            inferredCapabilityCode = inferPlatformCapability(menu.getParentCode());
        }
        if (inferredCapabilityCode == null) {
            return true;
        }
        return capabilityModuleRepo.findByCode(inferredCapabilityCode)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(true);
    }

    private String resolvePlatformCapability(Menu menu) {
        if (menu == null) {
            return null;
        }
        String capabilityCode = platformCapabilityOf(menu.getCode());
        if (capabilityCode != null) {
            return capabilityCode;
        }
        capabilityCode = platformCapabilityOf(menu.getParentCode());
        if (capabilityCode != null) {
            return capabilityCode;
        }
        return PLATFORM_MENU_CAPABILITIES.entrySet().stream()
                .filter(entry -> isDescendantCode(menu.getCode(), entry.getKey())
                        || isDescendantCode(menu.getParentCode(), entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String platformCapabilityOf(String menuCode) {
        return StringUtils.hasText(menuCode) ? PLATFORM_MENU_CAPABILITIES.get(menuCode) : null;
    }

    private String inferPlatformCapability(String menuCode) {
        if (!StringUtils.hasText(menuCode) || !menuCode.startsWith("platform:")) {
            return null;
        }
        String candidate = menuCode.substring("platform:".length()).split(":", 2)[0];
        return StringUtils.hasText(candidate) ? candidate : null;
    }

    private boolean isDescendantCode(String code, String parentCode) {
        return StringUtils.hasText(code) && StringUtils.hasText(parentCode) && code.startsWith(parentCode + ":");
    }

    private String resolveComponent(MenuNodeType type, String component) {
        if (type == MenuNodeType.LAYOUT || type == MenuNodeType.LINK) {
            return "Layout";
        }
        return component;
    }

    private String resolvePath(MenuNodeType type, String code, String path) {
        if (type == MenuNodeType.MENU || type == MenuNodeType.LAYOUT || type == MenuNodeType.LINK) {
            return StringUtils.hasText(path) ? path : "/" + code.replace(":", "/");
        }
        return null;
    }
}
