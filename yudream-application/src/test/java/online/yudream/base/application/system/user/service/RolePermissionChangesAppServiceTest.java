package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.user.cmd.RolePermissionChangesCmd;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.repo.RolePermissionChangesRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class RolePermissionChangesAppServiceTest {

    @Test
    void grantsActivePermissionsWithoutDroppingUnknownOrDeprecatedConfiguredCodes() {
        Fixture fixture = new Fixture(List.of("plugin:missing:use", "old:permission:view"));
        var result = fixture.service().apply(cmd(fixture.current, List.of("system:user:view"), List.of()));
        assertEquals(List.of("plugin:missing:use", "old:permission:view", "system:user:view"), fixture.next);
        assertEquals(fixture.current, fixture.expected);
        assertEquals(fixture.next, result.getPermissions());
        assertEquals(1, fixture.writes);
    }

    @Test
    void rejectsUnknownAndDeprecatedNewGrantsBeforeAnyWrite() {
        Fixture unknown = new Fixture(List.of("old"));
        assertTrue(assertThrows(BizException.class, () -> unknown.service()
                .apply(cmd(unknown.current, List.of("unknown"), List.of()))).getMessage().contains("当前有效"));
        assertEquals(0, unknown.writes);
        Fixture deprecated = new Fixture(List.of());
        Permission retired = Permission.create("old:permission:view", "旧权限", "测试", "旧权限");
        retired.deprecate();
        deprecated.active = List.of(retired);
        assertThrows(BizException.class, () -> deprecated.service()
                .apply(cmd(deprecated.current, List.of("old:permission:view"), List.of())));
        assertEquals(0, deprecated.writes);
    }

    @Test
    void removesUnknownCodesOnlyWhenExplicitlyRevoked() {
        Fixture fixture = new Fixture(List.of("unknown:a", "unknown:b", "system:user:view"));
        fixture.service().apply(cmd(fixture.current, List.of(), List.of("unknown:a")));
        assertEquals(List.of("unknown:b", "system:user:view"), fixture.next);
    }

    @Test
    void rejectsSuperAdministratorIncludingANoOp() {
        Fixture fixture = new Fixture(List.of());
        fixture.superAdmin = true;
        assertTrue(assertThrows(BizException.class, () -> fixture.service()
                .apply(cmd(List.of(), List.of(), List.of()))).getMessage().contains("通配权限"));
        assertEquals(0, fixture.writes);
    }

    @Test
    void rejectsConfiguredWildcardWithoutDeletingOrFilteringIt() {
        Fixture fixture = new Fixture(List.of("*", "unknown"));
        assertTrue(assertThrows(BizException.class, () -> fixture.service()
                .apply(cmd(fixture.current, List.of(), List.of("unknown")))).getMessage().contains("通配权限"));
        assertEquals(List.of("*", "unknown"), fixture.current);
        assertEquals(0, fixture.writes);
    }

    @Test
    void rejectsBothAnOutdatedSnapshotAndARaceAtTheAtomicWrite() {
        Fixture outdated = new Fixture(List.of("other"));
        assertEquals("角色授权已变化，请刷新后重试", assertThrows(BizException.class, () -> outdated.service()
                .apply(cmd(List.of(), List.of("system:user:view"), List.of()))).getMessage());
        assertEquals(0, outdated.writes);
        Fixture raced = new Fixture(List.of());
        raced.match = false;
        assertEquals("角色授权已变化，请刷新后重试", assertThrows(BizException.class, () -> raced.service()
                .apply(cmd(List.of(), List.of("system:user:view"), List.of()))).getMessage());
        assertEquals(1, raced.writes);
    }

    @Test
    void rejectsOverlappingGrantAndRevoke() {
        Fixture fixture = new Fixture(List.of());
        assertTrue(assertThrows(BizException.class, () -> fixture.service()
                .apply(cmd(List.of(), List.of("system:user:view"), List.of("system:user:view"))))
                .getMessage().contains("同时授予和撤销"));
        assertEquals(0, fixture.writes);
    }

    @Test
    void preservesOriginalArrayOrderAndRejectsReorderedSnapshot() {
        Fixture fixture = new Fixture(List.of("b", "a"));
        assertThrows(BizException.class, () -> fixture.service().apply(cmd(List.of("a", "b"), List.of(), List.of())));
        assertEquals(0, fixture.writes);
    }

    @Test
    void noOpDoesNotWriteAndTheAtomicResponseKeepsConcurrentBasicChanges() {
        Fixture noOp = new Fixture(List.of("unknown", "unknown"));
        assertEquals(noOp.current, noOp.service().apply(cmd(noOp.current, List.of("unknown"), List.of())).getPermissions());
        assertEquals(0, noOp.writes);
        assertEquals(0, noOp.catalogReads);
        Fixture concurrent = new Fixture(List.of());
        concurrent.updatedName = "另一管理员修改后的名称";
        assertEquals(concurrent.updatedName, concurrent.service()
                .apply(cmd(List.of(), List.of("system:user:view"), List.of())).getName());
    }

    private RolePermissionChangesCmd cmd(List<String> expected, List<String> grant, List<String> revoke) {
        RolePermissionChangesCmd cmd = new RolePermissionChangesCmd();
        cmd.setId(9007199254740993L);
        cmd.setExpectedPermissions(expected);
        cmd.setGrant(grant);
        cmd.setRevoke(revoke);
        return cmd;
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> action.apply(method.getName(), args));
    }

    private static class Fixture {
        final List<String> current;
        List<Permission> active = List.of(Permission.create("system:user:view", "查看用户", "用户", "查看用户"));
        List<String> next;
        List<String> expected;
        boolean superAdmin;
        boolean match = true;
        int writes;
        int catalogReads;
        String updatedName = "角色";

        Fixture(List<String> current) {
            this.current = current;
        }

        Role role(List<String> codes, String name) {
            return Role.builder().id(9007199254740993L).name(name).code("operator").status(RoleStatus.ACTIVE)
                    .systemType(superAdmin ? SystemRoleType.SUPER_ADMIN : null)
                    .permissions(codes.stream().map(PermissionID::of).toList()).build();
        }

        @SuppressWarnings("unchecked")
        RolePermissionChangesAppService service() {
            RoleRepo roles = stub(RoleRepo.class, (method, args) -> Optional.of(role(current, "原名称")));
            PermissionRepo permissions = stub(PermissionRepo.class, (method, args) -> {
                catalogReads++;
                return active;
            });
            DeptRepo depts = stub(DeptRepo.class, (method, args) -> List.of());
            RolePermissionChangesRepo changes = (id, baseline, updated) -> {
                writes++;
                assertEquals(9007199254740993L, id);
                expected = baseline;
                next = updated;
                return match ? Optional.of(role(updated, updatedName)) : Optional.empty();
            };
            return new RolePermissionChangesAppService(roles, permissions, depts, changes);
        }
    }
}
