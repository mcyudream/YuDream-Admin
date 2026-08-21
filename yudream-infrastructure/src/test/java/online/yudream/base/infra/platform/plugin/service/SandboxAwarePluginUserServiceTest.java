package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.application.platform.plugin.service.PluginUserFrameworkService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.spi.system.user.PluginUserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxAwarePluginUserServiceTest {

    private static final PluginUserProfile PROFILE = new PluginUserProfile(1L, "tester", "Tester", null, null,
            "10001", null, "ACTIVE");

    @Test
    void passesThroughOutsideSandbox() {
        StubDelegate delegate = new StubDelegate();
        SandboxAwarePluginUserService service = new SandboxAwarePluginUserService(delegate, roleRepo(List.of()));

        assertEquals(Optional.of(PROFILE), service.findByQq("10001"));
        assertEquals(List.of(new PluginUserRole(1L, "admin", "管理员")), service.listRoles(1L));
        assertEquals(0, delegate.bindCalls);
    }

    @Test
    void forceUnboundHidesBindingInsideSandbox() {
        StubDelegate delegate = new StubDelegate();
        SandboxAwarePluginUserService service = new SandboxAwarePluginUserService(delegate, roleRepo(List.of()));
        QqSandboxSession session = session(true, null);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            assertEquals(Optional.empty(), service.findByQq("10001"));
        }
        assertTrue(session.timeline().stream().anyMatch(event -> "identity.override".equals(event.action())
                && "forceUnbound".equals(event.payload().get("type"))));
    }

    @Test
    void simulateRolesOverrideRealRoles() {
        StubDelegate delegate = new StubDelegate();
        Role admin = Role.create("管理员", "admin", DeptID.of(1L), RoleLevel.ADMIN);
        SandboxAwarePluginUserService service = new SandboxAwarePluginUserService(delegate, roleRepo(List.of(admin)));
        QqSandboxSession session = session(false, List.of("admin", "missing"));

        List<PluginUserRole> roles;
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            roles = service.listRoles(99L);
        }
        assertEquals(1, roles.size());
        assertEquals("admin", roles.get(0).code());
        assertTrue(session.timeline().stream().anyMatch(event -> "identity.override".equals(event.action())
                && "simulateRoles".equals(event.payload().get("type"))
                && List.of("missing").equals(event.payload().get("unknownRoles"))));
    }

    @Test
    void emptySimulateRolesMeansNoRoles() {
        StubDelegate delegate = new StubDelegate();
        SandboxAwarePluginUserService service = new SandboxAwarePluginUserService(delegate, roleRepo(List.of()));
        QqSandboxSession session = session(false, List.of());

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            assertTrue(service.listRoles(1L).isEmpty());
        }
    }

    @Test
    void nullSimulateRolesKeepsRealRoles() {
        StubDelegate delegate = new StubDelegate();
        SandboxAwarePluginUserService service = new SandboxAwarePluginUserService(delegate, roleRepo(List.of()));
        QqSandboxSession session = session(false, null);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            assertEquals(List.of(new PluginUserRole(1L, "admin", "管理员")), service.listRoles(1L));
        }
    }

    @Test
    void blocksUserWritesInsideSandbox() {
        StubDelegate delegate = new StubDelegate();
        SandboxAwarePluginUserService service = new SandboxAwarePluginUserService(delegate, roleRepo(List.of()));
        QqSandboxSession session = session(false, null);

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            assertThrows(BizException.class, () -> service.bindQqOnce(1L, "10001"));
        }
        assertEquals(0, delegate.bindCalls);
        assertTrue(session.timeline().stream().anyMatch(event -> "identity.override".equals(event.action())
                && "writeBlocked".equals(event.payload().get("type"))));
    }

    private QqSandboxSession session(boolean forceUnbound, List<String> simulateRoles) {
        return QqSandboxSession.create("s1", "demo", "1", "10000", "10001", "Tester", "20001", "group",
                QqSandboxRandomMode.REAL, forceUnbound, simulateRoles, 60_000L, Instant.now());
    }

    private RoleRepo roleRepo(List<Role> roles) {
        return (RoleRepo) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RoleRepo.class},
                (proxy, method, args) -> "findAll".equals(method.getName()) ? roles : null);
    }

    private static final class StubDelegate extends PluginUserFrameworkService {
        private int bindCalls;

        private StubDelegate() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Optional<PluginUserProfile> findByQq(String qq) {
            return Optional.of(PROFILE);
        }

        @Override
        public List<PluginUserRole> listRoles(Long userId) {
            return List.of(new PluginUserRole(1L, "admin", "管理员"));
        }

        @Override
        public void bindQqOnce(Long userId, String qq) {
            bindCalls++;
        }
    }
}
