package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.user.cmd.UserRoleChangesCmd;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.repo.UserRoleChangesRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class UserRoleChangesAppServiceTest {

    @Test
    void addsAnActiveRoleInAnAssignedDepartmentAndKeepsUnknownRolesAndLongIds() {
        Fixture fixture = new Fixture(List.of(9007199254740993L, 999L));
        fixture.service().apply(cmd(fixture.current, List.of("2"), List.of()));
        assertEquals(List.of(9007199254740993L, 999L, 2L), fixture.next);
        assertEquals(fixture.current, fixture.expected);
        assertEquals(List.of(10L), fixture.requiredDeptIds);
        assertEquals(1, fixture.detailReads);
    }

    @Test
    void rejectsMissingAndDisabledNewRoles() {
        Fixture missing = new Fixture(List.of());
        missing.availableRoles = List.of();
        assertTrue(assertThrows(BizException.class, () -> missing.service().apply(cmd(List.of(), List.of("2"), List.of())))
                .getMessage().contains("不存在"));
        assertEquals(0, missing.writes);
        Fixture disabled = new Fixture(List.of());
        disabled.availableRoles = List.of(role(2, 10, RoleStatus.DEPRECATED));
        assertTrue(assertThrows(BizException.class, () -> disabled.service().apply(cmd(List.of(), List.of("2"), List.of())))
                .getMessage().contains("停用"));
        assertEquals(0, disabled.writes);
    }

    @Test
    void rejectsANewRoleOutsideTheUsersAssignedDepartments() {
        Fixture fixture = new Fixture(List.of());
        fixture.availableRoles = List.of(role(2, 20, RoleStatus.ACTIVE));
        assertTrue(assertThrows(BizException.class, () -> fixture.service().apply(cmd(List.of(), List.of("2"), List.of())))
                .getMessage().contains("已分配部门"));
        assertEquals(0, fixture.writes);
    }

    @Test
    void onlyAnExplicitRevokeRemovesAnUnknownConfiguredRole() {
        Fixture fixture = new Fixture(List.of(1L, 999L, 998L));
        fixture.service().apply(cmd(fixture.current, List.of(), List.of("999")));
        assertEquals(List.of(1L, 998L), fixture.next);
        assertEquals(List.of(), fixture.requiredDeptIds);
        assertEquals(0, fixture.roleReads);
    }

    @Test
    void rejectsStaleSnapshotsAndConcurrentRoleOrDepartmentChanges() {
        Fixture stale = new Fixture(List.of(1L));
        assertEquals("用户角色已变化，请刷新后重试", assertThrows(BizException.class, () -> stale.service()
                .apply(cmd(List.of(), List.of("2"), List.of()))).getMessage());
        assertEquals(0, stale.writes);
        Fixture raced = new Fixture(List.of());
        raced.match = false;
        assertEquals("用户角色或部门已变化，请刷新后重试", assertThrows(BizException.class, () -> raced.service()
                .apply(cmd(List.of(), List.of("2"), List.of()))).getMessage());
        assertEquals(0, raced.detailReads);
    }

    @Test
    void rejectsOverlappingChangesAndMissingSnapshot() {
        Fixture overlap = new Fixture(List.of());
        assertTrue(assertThrows(BizException.class, () -> overlap.service()
                .apply(cmd(List.of(), List.of("2"), List.of("2")))).getMessage().contains("同时分配和移除"));
        assertEquals(0, overlap.writes);
        UserRoleChangesCmd missing = cmd(List.of(), List.of(), List.of());
        missing.setExpectedRoleIds(null);
        assertThrows(BizException.class, () -> new Fixture(List.of()).service().apply(missing));
    }

    @Test
    void noOpPreservesUnknownRolesWithoutLookingUpOrWritingRoles() {
        Fixture fixture = new Fixture(List.of(999L));
        fixture.service().apply(cmd(fixture.current, List.of("999"), List.of()));
        assertEquals(0, fixture.writes);
        assertEquals(0, fixture.roleReads);
        assertEquals(1, fixture.detailReads);
    }

    @Test
    void rejectsAnInvalidRoleIdAtTheApplicationBoundary() {
        Fixture fixture = new Fixture(List.of());
        assertEquals("角色标识无效", assertThrows(BizException.class, () -> fixture.service()
                .apply(cmd(List.of(), List.of("NaN"), List.of()))).getMessage());
        assertEquals(0, fixture.writes);
    }

    private UserRoleChangesCmd cmd(List<Long> expected, List<String> grant, List<String> revoke) {
        UserRoleChangesCmd cmd = new UserRoleChangesCmd();
        cmd.setId(55L);
        cmd.setExpectedRoleIds(expected.stream().map(String::valueOf).toList());
        cmd.setGrant(grant);
        cmd.setRevoke(revoke);
        return cmd;
    }

    private static Role role(long id, long dept, RoleStatus status) {
        return Role.builder().id(id).deptId(DeptID.of(dept)).status(status).build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> action.apply(method.getName(), args));
    }

    private static class Fixture {
        final List<Long> current;
        List<Role> availableRoles = List.of(role(2, 10, RoleStatus.ACTIVE));
        List<Long> next;
        List<Long> expected;
        List<Long> requiredDeptIds;
        boolean match = true;
        int writes;
        int roleReads;
        int detailReads;

        Fixture(List<Long> current) {
            this.current = current;
        }

        UserRoleChangesAppService service() {
            UserRepo users = stub(UserRepo.class, (method, args) -> Optional.of(User.builder().id(55L)
                    .roles(current.stream().map(RoleID::of).toList()).depts(List.of(new UserDept(DeptID.of(10L), true))).build()));
            RoleRepo roles = stub(RoleRepo.class, (method, args) -> {
                roleReads++;
                return availableRoles;
            });
            UserRoleChangesRepo changes = (id, baseline, updated, departments) -> {
                assertEquals(55L, id);
                writes++;
                expected = baseline;
                next = updated;
                requiredDeptIds = departments;
                return match;
            };
            UserManageAppService details = new UserManageAppService(null, null, null, null, null) {
                @Override
                public UserManageDTO get(Long id) {
                    detailReads++;
                    return UserManageDTO.builder().id(id).roleIds(next == null ? current : next).build();
                }
            };
            return new UserRoleChangesAppService(users, roles, changes, details);
        }
    }
}
