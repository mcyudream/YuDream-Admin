package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.application.system.user.assembler.PermissionOverviewAssembler;
import online.yudream.base.application.system.user.assembler.UserAssembler;
import online.yudream.base.application.system.user.dto.PermissionOverviewDTO;
import online.yudream.base.application.system.user.dto.PermissionOverviewDTO.MenuNodeDTO;
import online.yudream.base.application.system.user.dto.PermissionOverviewDTO.StatisticsDTO;
import online.yudream.base.application.system.user.dto.PermissionOverviewDTO.UserNodeDTO;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.application.system.user.query.PermissionOverviewQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.PermissionOverviewUserRepo;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.service.UserContextStore;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.UserPermissionStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionOverviewAppService {

    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;
    private final PermissionRepo permissionRepo;
    private final PermissionOverviewUserRepo overviewUserRepo;
    private final UserContextStore userContextStore;
    private final MenuRepo menuRepo;
    private final MenuAppService menuAppService;

    @Transactional(readOnly = true)
    public PermissionOverviewDTO overview(PermissionOverviewQuery query, boolean canViewUsers, boolean canViewMenus) {
        PermissionOverviewQuery criteria = query == null ? new PermissionOverviewQuery() : query;
        int page = Math.max(criteria.getUserPage(), 1);
        int size = Math.min(Math.max(criteria.getUserSize(), 1), 100);
        List<Role> roles = roleRepo.findAll();
        Map<Long, Dept> deptMap = deptRepo.findByIds(roles.stream().map(Role::getDeptId).filter(Objects::nonNull)
                        .map(DeptID::getValue).distinct().toList()).stream()
                .collect(Collectors.toMap(Dept::getId, dept -> dept, (left, right) -> left));
        List<RoleManageDTO> roleDTOs = roles.stream().map(role -> UserAssembler.toRoleManageDTO(role, deptMap)).toList();

        List<UserNodeDTO> users = List.of();
        Long userTotal = null;
        UserPermissionStatistics userStats = null;
        List<String> idleRoleIds = null;
        if (canViewUsers) {
            PageResult<User> userPage = overviewUserRepo.page(criteria.getUserKeyword(), criteria.getUserRoleId(),
                    criteria.isUserUnassignedOnly(), page, size);
            page = userPage.getPage();
            size = userPage.getSize();
            users = userPage.getRecords().stream().map(user -> PermissionOverviewAssembler.toUserDTO(user, roles,
                    userContextStore.getCurrentDeptId(user.getId()), userContextStore.getCurrentRoleId(user.getId()))).toList();
            userTotal = userPage.getTotal();
            userStats = overviewUserRepo.statistics();
            Set<Long> assignedRoleIds = userStats.assignedRoleIds();
            idleRoleIds = roles.stream().map(Role::getId).filter(id -> !assignedRoleIds.contains(id))
                    .map(String::valueOf).toList();
        }

        List<MenuNodeDTO> menus = List.of();
        Long menuCount = null;
        Long ungrantedMenuCount = null;
        if (canViewMenus) {
            List<Menu> allMenus = menuRepo.findAll().stream().collect(Collectors.toMap(Menu::getCode,
                    menu -> menu, (left, right) -> left, LinkedHashMap::new)).values().stream().toList();
            Set<String> availableCodes = menuAppService.runtimeAvailableMenuCodes(allMenus);
            Map<String, Set<String>> roleMenus = roles.stream().filter(role -> role.getStatus() == RoleStatus.ACTIVE)
                    .collect(Collectors.toMap(role -> String.valueOf(role.getId()),
                            role -> menuAppService.visibleMenuCodes(effectiveRolePermissions(role), allMenus, availableCodes),
                            (left, right) -> left, LinkedHashMap::new));
            menus = allMenus.stream().map(menu -> PermissionOverviewAssembler.toMenuDTO(menu,
                    availableCodes.contains(menu.getCode()), roleMenus.entrySet().stream()
                            .filter(entry -> entry.getValue().contains(menu.getCode())).map(Map.Entry::getKey).toList())).toList();
            menuCount = (long) menus.size();
            Set<String> parentCodes = allMenus.stream().filter(menu -> menu.getStatus() == MenuStatus.ACTIVE)
                    .filter(menu -> availableCodes.contains(menu.getCode())).map(Menu::getParentCode)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            ungrantedMenuCount = menus.stream().filter(menu -> menu.status() == MenuStatus.ACTIVE && menu.runtimeAvailable())
                    .filter(menu -> menu.type() == MenuNodeType.MENU || menu.type() == MenuNodeType.LINK || menu.type() == MenuNodeType.BUTTON)
                    .filter(menu -> !parentCodes.contains(menu.code()))
                    .filter(menu -> menu.permission() != null && !menu.permission().isBlank() && !"*".equals(menu.permission()))
                    .filter(menu -> menu.roleIds().isEmpty()).count();
        }
        StatisticsDTO stats = new StatisticsDTO(userStats == null ? null : userStats.userCount(), roles.size(), menuCount,
                idleRoleIds == null ? null : (long) idleRoleIds.size(), userStats == null ? null : userStats.unassignedUserCount(),
                ungrantedMenuCount, userStats == null ? null : userStats.assignmentCount());
        return new PermissionOverviewDTO(roleDTOs, permissionRepo.findAll().stream().map(UserAssembler::toPermissionDTO).toList(),
                users, menus, stats, page, size, userTotal, canViewUsers, canViewMenus, idleRoleIds);
    }

    private List<String> effectiveRolePermissions(Role role) {
        if (role.getSystemType() == SystemRoleType.SUPER_ADMIN) {
            return List.of("*");
        }
        return role.getPermissions() == null ? List.of() : role.getPermissions().stream().map(PermissionID::getCode).toList();
    }
}
