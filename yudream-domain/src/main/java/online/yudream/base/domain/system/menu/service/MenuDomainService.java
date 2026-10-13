package online.yudream.base.domain.system.menu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.enumerate.SeedSyncMode;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.enumerate.PermissionSource;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.security.PermissionMeta;
import online.yudream.base.domain.system.security.PermissionCatalogNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 菜单领域服务。
 */
@Slf4j
@RequiredArgsConstructor
public class MenuDomainService {

    private final MenuRepo menuRepo;
    private final PermissionRepo permissionRepo;

    /**
     * 同步菜单树到数据库，只为真实叶子权限补充 MENU 来源登记。
     *
     * @param menus 解析后的菜单树
     */
    public List<Menu> syncMenus(Collection<Menu> menus) {
        return syncMenus(menus, SeedSyncMode.OVERWRITE);
    }

    public List<Menu> syncMenus(Collection<Menu> menus, SeedSyncMode mode) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        List<Menu> flattened = flatten(menus);
        SeedSyncMode syncMode = mode == null ? SeedSyncMode.MISSING_ONLY : mode;
        if (syncMode == SeedSyncMode.INIT_EMPTY && menuRepo.count() > 0) {
            log.info("Skip menu seed sync because menu data already exists, mode={}", syncMode);
            return List.of();
        }
        List<Menu> synced = new ArrayList<>();
        for (Menu menu : flattened) {
            if (syncMode == SeedSyncMode.MISSING_ONLY && menuRepo.existsByCode(menu.getCode())) {
                continue;
            }
            synced.add(syncMenu(menu));
        }
        return synced;
    }

    public Menu syncMenu(Menu menu) {
        assertSafeLink(menu.getLink());
        Menu saved = menuRepo.save(menu);
        log.debug("Synced menu: code={}, name={}", saved.getCode(), saved.getName());
        upsertMenuPermission(saved);
        return saved;
    }

    public void deleteMenu(String code) {
        Menu menu = menuRepo.findByCode(code).orElse(null);
        menuRepo.deleteByCode(code);
        if (menu != null && permissionLeaf(menu)) {
            Permission permission = permissionRepo.findByCode(menu.getPermissionCode()).orElse(null);
            if (permission != null && permission.getSource() == PermissionSource.MENU) {
                List<Menu> remaining = menuRepo.findAll();
                boolean referenced = remaining != null && remaining.stream().anyMatch(candidate -> permissionLeaf(candidate)
                        && menu.getPermissionCode().equals(candidate.getPermissionCode()));
                if (referenced) {
                    syncPermissionCatalog(remaining, menu.getPermissionCode());
                } else {
                    permissionRepo.deleteByCode(menu.getPermissionCode());
                }
            }
        }
        log.debug("Deleted menu: code={}", code);
    }

    /**
     * 菜单外链 scheme 白名单（http/https）：link 会在控制台绑定到 href/window.open，
     * javascript:/data: 等值构成存储型 XSS；插件清单与 HTTP 管理两条写入路径统一在此校验。
     */
    private void assertSafeLink(String link) {
        if (link == null || link.isBlank()) {
            return;
        }
        String lower = link.trim().toLowerCase(java.util.Locale.ROOT);
        if ((lower.startsWith("http://") || lower.startsWith("https://"))) {
            try {
                if (java.net.URI.create(link.trim()).getHost() != null) {
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // 落入拒绝
            }
        }
        throw new online.yudream.base.domain.common.exception.BizException("菜单外链仅支持 http/https 地址");
    }

    private void upsertMenuPermission(Menu menu) {
        if (!permissionLeaf(menu)) {
            return;
        }
        List<Menu> allMenus = new ArrayList<>();
        List<Menu> persisted = menuRepo.findAll();
        if (persisted != null) {
            persisted.stream().filter(candidate -> !menu.getCode().equals(candidate.getCode())).forEach(allMenus::add);
        }
        allMenus.add(menu);
        syncPermissionCatalog(allMenus, menu.getPermissionCode());
    }

    /** 仅同步权限目录，运维重建时不保存菜单、不调整角色配置。 */
    public int syncPermissionCatalog(Collection<Menu> menus) {
        return syncPermissionCatalog(menus, null);
    }

    private int syncPermissionCatalog(Collection<Menu> menus, String selectedCode) {
        int updated = 0;
        for (Permission proposed : planPermissionCatalog(menus)) {
            String code = proposed.getId().getCode();
            if (selectedCode != null && !selectedCode.equals(code)) {
                continue;
            }
            Permission existing = permissionRepo.findByCode(code).orElse(null);
            // 真实端点和插件/人工登记拥有元数据；菜单只能补缺，不能夺取来源或改写名称。
            if (existing != null && existing.getSource() != PermissionSource.MENU) {
                continue;
            }
            Permission permission = existing == null ? proposed : existing;
            permission.update(proposed.getName(), proposed.getModule(), proposed.getDescription());
            permission.setSource(PermissionSource.MENU);
            permission.setStatus(proposed.getStatus());
            permissionRepo.save(permission);
            updated++;
        }
        return updated;
    }

    /** 纯计算重建计划，供运维在任何写入前审查；不会访问仓储。 */
    public static List<Permission> planPermissionCatalog(Collection<Menu> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        List<Menu> allMenus = flatten(menus);
        Map<String, Menu> menuMap = allMenus.stream()
                .collect(Collectors.toMap(Menu::getCode, menu -> menu, (left, right) -> left));
        Map<String, List<Menu>> groups = allMenus.stream().filter(MenuDomainService::permissionLeaf)
                .collect(Collectors.groupingBy(Menu::getPermissionCode, TreeMap::new, Collectors.toList()));
        List<Permission> plan = new ArrayList<>();
        for (Map.Entry<String, List<Menu>> entry : groups.entrySet()) {
            List<PermissionMeta> candidates = entry.getValue().stream().map(menu -> menuPermissionMeta(menu, menuMap)).toList();
            PermissionMeta meta = PermissionCatalogNormalizer.normalize(candidates).getFirst();
            Permission permission = Permission.create(meta.code(), meta.name(), meta.module(), meta.desc(), PermissionSource.MENU);
            permission.setStatus(entry.getValue().stream().anyMatch(menu -> menu.getStatus() == MenuStatus.ACTIVE && menu.isAvailableForRuntime())
                    ? PermissionStatus.ACTIVE : PermissionStatus.DEPRECATED);
            plan.add(permission);
        }
        return plan;
    }

    private static boolean permissionLeaf(Menu menu) {
        return (menu.getType() == MenuNodeType.MENU || menu.getType() == MenuNodeType.LINK || menu.getType() == MenuNodeType.BUTTON)
                && menu.getPermissionCode() != null && !menu.getPermissionCode().isBlank();
    }

    private static PermissionMeta menuPermissionMeta(Menu menu, Map<String, Menu> menus) {
        String code = menu.getPermissionCode();
        String action = PermissionCatalogNormalizer.actionName(code);
        String access = menu.getType() == MenuNodeType.LINK ? "访问外链入口" : menu.getType() == MenuNodeType.BUTTON ? "执行按钮操作" : "访问页面入口";
        String name = (action == null ? access : action + "权限") + "（" + code + "）";
        String module = menu.getModule();
        Menu moduleMenu = module == null ? null : menus.get(module);
        if (moduleMenu != null && (moduleMenu.getType() == MenuNodeType.CATEGORY || moduleMenu.getType() == MenuNodeType.LAYOUT)) {
            module = moduleMenu.getName();
        }
        String description = access + "「" + menu.getName() + "」，权限码：" + code;
        return new PermissionMeta(code, name, module, description);
    }

    private static List<Menu> flatten(Collection<Menu> menus) {
        return menus.stream()
                .flatMap(MenuDomainService::flattenWithChildren)
                .collect(Collectors.toList());
    }

    private static java.util.stream.Stream<Menu> flattenWithChildren(Menu menu) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(menu),
                menu.getChildren() == null ? java.util.stream.Stream.empty() : flatten(menu.getChildren()).stream()
        );
    }

    /**
     * 查找所有有效的菜单/目录节点（不含按钮）。
     */
    public List<Menu> findActiveMenus() {
        return menuRepo.findByTypeIn(List.of(MenuNodeType.CATEGORY, MenuNodeType.LAYOUT, MenuNodeType.MENU, MenuNodeType.LINK)).stream()
                .filter(m -> m.getStatus() == MenuStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    /**
     * 按 code 自愈清理重复菜单（导入合并/历史原因产生同 code 多条时保留首条，删除冗余后重建首条）。
     * 子节点按 parentCode 挂接，删除冗余副本不影响树结构。
     *
     * @return 清理的冗余菜单数量
     */
    public int dedupeByCode() {
        List<Menu> all = menuRepo.findAll();
        Map<String, Menu> keptByCode = new LinkedHashMap<>();
        List<Menu> duplicates = new ArrayList<>();
        for (Menu menu : all) {
            Menu kept = keptByCode.putIfAbsent(menu.getCode(), menu);
            if (kept != null) {
                duplicates.add(menu);
            }
        }
        if (duplicates.isEmpty()) {
            return 0;
        }
        Set<String> healedCodes = new LinkedHashSet<>();
        for (Menu duplicate : duplicates) {
            if (healedCodes.add(duplicate.getCode())) {
                menuRepo.deleteByCode(duplicate.getCode());
                Menu kept = keptByCode.get(duplicate.getCode());
                if (kept != null) {
                    menuRepo.save(kept);
                }
            }
        }
        log.warn("Menu sync detected {} duplicate menu docs, cleaned redundant copies for codes: {}",
                duplicates.size(), healedCodes);
        return duplicates.size();
    }

    /**
     * 按 code 构建菜单索引（重复 code 保留先加载的一条）。
     */
    public Map<String, Menu> findAllMap() {
        return findActiveMenus().stream()
                .collect(Collectors.toMap(Menu::getCode, m -> m, (first, second) -> first));
    }
}
