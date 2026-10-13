package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.application.system.user.dto.PermissionOverviewDTO;
import online.yudream.base.application.system.user.query.PermissionOverviewQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.PermissionOverviewUserRepo;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.service.UserContextStore;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import online.yudream.base.domain.system.user.valobj.UserPermissionStatistics;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class PermissionOverviewAppServiceTest {

    @Test
    void hidesUserAndMenuDataWithoutTheirViewPermissionsAndPreservesUnknownGrants() {
        Role role = role(1, 10, RoleStatus.ACTIVE, null, "retired:plugin:execute");
        PermissionOverviewDTO result = service(List.of(role), List.of(), new Users(), context(null, null), true)
                .overview(new PermissionOverviewQuery(), false, false);

        assertEquals(List.of("retired:plugin:execute"), result.roles().getFirst().getPermissions());
        assertEquals(1, result.stats().roleCount());
        assertTrue(result.users().isEmpty());
        assertTrue(result.menus().isEmpty());
        assertNull(result.stats().userCount());
        assertNull(result.stats().idleRoleCount());
        assertNull(result.stats().menuCount());
        assertNull(result.stats().assignmentCount());
        assertNull(result.userTotal());
        assertNull(result.idleRoleIds());
        assertFalse(result.canViewUsers());
        assertFalse(result.canViewMenus());
    }

    @Test
    void keepsGlobalStatisticsIndependentOfFilteredUserPageAndClampsPagination() {
        Users users = new Users();
        users.records = List.of(user(99, UserStatus.DISABLED, false, List.of(1L), 10));
        users.stats = new UserPermissionStatistics(800, 19, 1234, Set.of(1L, 2L));
        PermissionOverviewQuery query = new PermissionOverviewQuery();
        query.setUserPage(-1);
        query.setUserSize(1000);
        query.setUserKeyword("operator");
        query.setUserRoleId(1L);
        query.setUserUnassignedOnly(true);
        PermissionOverviewDTO result = service(List.of(role(1, 10, RoleStatus.ACTIVE, null),
                        role(2, 10, RoleStatus.DEPRECATED, null), role(3, 10, RoleStatus.ACTIVE, null)),
                List.of(), users, context(null, null), false).overview(query, true, false);

        assertEquals(1, users.page);
        assertEquals(100, users.size);
        assertEquals("operator", users.keyword);
        assertEquals(1L, users.roleId);
        assertTrue(users.unassigned);
        assertEquals(800L, result.stats().userCount());
        assertEquals(1234L, result.stats().assignmentCount());
        assertEquals(19L, result.stats().unassignedUserCount());
        assertEquals(27L, result.userTotal());
        assertEquals(List.of("3"), result.idleRoleIds());
        assertEquals(1L, result.stats().idleRoleCount());
        assertEquals(UserStatus.DISABLED, result.users().getFirst().status());
        assertFalse(result.users().getFirst().emailVerified());
    }

    @Test
    void resolvesOneRoleInTheSelectedDepartmentAndReportsDefaultContext() {
        Users users = new Users();
        users.records = List.of(user(99, UserStatus.ACTIVE, true, List.of(1L, 2L), 10));
        List<Role> roles = List.of(role(1, 10, RoleStatus.ACTIVE, null), role(2, 20, RoleStatus.ACTIVE, null));
        PermissionOverviewDTO session = service(roles, List.of(), users, context(20L, 1L), false)
                .overview(null, true, false);
        assertEquals("2", session.users().getFirst().currentRoleId());
        assertEquals("20", session.users().getFirst().currentDeptId());
        assertEquals("SESSION", session.users().getFirst().contextSource());

        PermissionOverviewDTO defaults = service(roles, List.of(), users, context(null, null), false)
                .overview(null, true, false);
        assertEquals(List.of("1", "2"), defaults.users().getFirst().roleIds());
        assertEquals("1", defaults.users().getFirst().currentRoleId());
        assertEquals("DEFAULT", defaults.users().getFirst().contextSource());
    }

    @Test
    void inheritsButtonPermissionsToPageAndAncestorsAndIgnoresUnavailablePluginButtons() {
        List<Menu> menus = List.of(menu("root", MenuNodeType.CATEGORY, null),
                menu("page", MenuNodeType.MENU, "root"), menu("button", MenuNodeType.BUTTON, "page"),
                menu("other", MenuNodeType.MENU, "root"),
                Menu.builder().code("plugin-button").name("插件按钮").type(MenuNodeType.BUTTON)
                        .parentCode("other").source(MenuSource.PLUGIN).runtimeAvailable(false).build(),
                menu("platform:wiki", MenuNodeType.MENU, "root"));
        PermissionOverviewDTO result = service(List.of(role(1, 10, RoleStatus.ACTIVE, null, "button", "plugin-button"),
                        role(2, 10, RoleStatus.DEPRECATED, null, "other")),
                menus, new Users(), context(null, null), false).overview(null, false, true);
        assertEquals(List.of("1"), findMenu(result, "button").roleIds());
        assertEquals(List.of("1"), findMenu(result, "page").roleIds());
        assertEquals(List.of("1"), findMenu(result, "root").roleIds());
        assertTrue(findMenu(result, "other").roleIds().isEmpty());
        assertFalse(findMenu(result, "plugin-button").runtimeAvailable());
        assertTrue(findMenu(result, "plugin-button").roleIds().isEmpty());
        assertFalse(findMenu(result, "platform:wiki").runtimeAvailable());
        assertEquals(1L, result.stats().ungrantedMenuCount());
    }

    @Test
    void superAdministratorSeesRuntimeMenusEvenWithNoStoredPermissionCodes() {
        List<Menu> menus = List.of(menu("root", MenuNodeType.CATEGORY, null), menu("page", MenuNodeType.MENU, "root"));
        PermissionOverviewDTO result = service(List.of(role(7, 10, RoleStatus.ACTIVE, SystemRoleType.SUPER_ADMIN)),
                menus, new Users(), context(null, null), false).overview(null, false, true);
        assertEquals(List.of("7"), findMenu(result, "page").roleIds());
        assertEquals(0L, result.stats().ungrantedMenuCount());
    }

    private PermissionOverviewDTO.MenuNodeDTO findMenu(PermissionOverviewDTO dto, String code) {
        return dto.menus().stream().filter(menu -> code.equals(menu.code())).findFirst().orElseThrow();
    }

    private PermissionOverviewAppService service(List<Role> roles, List<Menu> menus, Users users,
                                                UserContextStore context, boolean denied) {
        RoleRepo roleRepo = stub(RoleRepo.class, (method, args) -> method.equals("findAll") ? roles : List.of());
        DeptRepo deptRepo = stub(DeptRepo.class, (method, args) -> List.of());
        PermissionRepo permissionRepo = stub(PermissionRepo.class, (method, args) -> List.of(
                Permission.create("system:role:view", "查看角色", "系统管理", "查看角色")));
        MenuRepo menuRepo = stub(MenuRepo.class, (method, args) -> {
            if (denied) {
                fail("无菜单查看权限不得查询菜单仓储");
            }
            return menus;
        });
        PermissionOverviewUserRepo userRepo = denied ? new PermissionOverviewUserRepo() {
            public PageResult<User> page(String keyword, Long roleId, boolean unassigned, int page, int size) {
                return fail("无用户查看权限不得查询用户仓储");
            }
            public UserPermissionStatistics statistics() {
                return fail("无用户查看权限不得读取关系统计");
            }
        } : users;
        CapabilityModuleRepo caps = stub(CapabilityModuleRepo.class, (method, args) -> Optional.empty());
        MenuAppService menuService = new MenuAppService(new MenuDomainService(menuRepo, permissionRepo), menuRepo, caps);
        return new PermissionOverviewAppService(roleRepo, deptRepo, permissionRepo, userRepo, context, menuRepo, menuService);
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> action.apply(method.getName(), args));
    }

    private Role role(long id, long dept, RoleStatus status, SystemRoleType type, String... permissions) {
        return Role.builder().id(id).name("角色" + id).code("role-" + id).deptId(DeptID.of(dept)).status(status)
                .systemType(type).permissions(List.of(permissions).stream().map(PermissionID::of).toList()).build();
    }

    private User user(long id, UserStatus status, boolean verified, List<Long> roles, long dept) {
        return User.builder().id(id).username("operator").nickname("操作员").status(status).emailVerified(verified)
                .roles(roles.stream().map(RoleID::of).toList()).depts(List.of(new UserDept(DeptID.of(dept), true))).build();
    }

    private Menu menu(String code, MenuNodeType type, String parent) {
        return Menu.builder().code(code).name(code).type(type).parentCode(parent).build();
    }

    private UserContextStore context(Long dept, Long role) {
        return stub(UserContextStore.class, (method, args) -> method.equals("getCurrentDeptId") ? dept : role);
    }

    private static final class Users implements PermissionOverviewUserRepo {
        List<User> records = List.of();
        UserPermissionStatistics stats = new UserPermissionStatistics(0, 0, 0, Set.of());
        int page;
        int size;
        String keyword;
        Long roleId;
        boolean unassigned;

        public PageResult<User> page(String keyword, Long roleId, boolean unassignedOnly, int page, int size) {
            this.page = page;
            this.size = size;
            this.keyword = keyword;
            this.roleId = roleId;
            this.unassigned = unassignedOnly;
            return new PageResult<>(records, 27, page, size);
        }
        public UserPermissionStatistics statistics() {
            return stats;
        }
    }
}
