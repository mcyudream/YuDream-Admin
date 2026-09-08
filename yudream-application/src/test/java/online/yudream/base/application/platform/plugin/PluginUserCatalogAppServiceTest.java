package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.dto.PluginDeptCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginRoleCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginUserCatalogDTO;
import online.yudream.base.application.platform.plugin.service.PluginUserCatalogAppService;
import online.yudream.base.application.system.user.dto.OptionDTO;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.plugin.spi.system.user.PluginDeptOption;
import online.yudream.base.plugin.spi.system.user.PluginUserCreate;
import online.yudream.base.plugin.spi.system.user.PluginUserDept;
import online.yudream.base.plugin.spi.system.user.PluginUserField;
import online.yudream.base.plugin.spi.system.user.PluginUserOption;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.spi.system.user.PluginUserProfileUpdate;
import online.yudream.base.plugin.spi.system.user.PluginUserRole;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import online.yudream.base.plugin.spi.system.user.PluginUserTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginUserCatalogAppServiceTest {

    @Test
    void mapsUserSearchResults() {
        PluginUserOption user = new PluginUserOption("9", "alice", "Alice", "a@x", "av", "ACTIVE", List.of("1"), List.of("技术部"));
        PluginUserCatalogAppService service = new PluginUserCatalogAppService(
                new StubUserService(List.of(user), List.of()),
                new StubRoleManageAppService(List.of()));

        List<PluginUserCatalogDTO> users = service.searchUsers("alice", "1", 1, 20);

        assertEquals(1, users.size());
        assertEquals("9", users.getFirst().getId());
        assertEquals("alice", users.getFirst().getUsername());
        assertEquals("Alice", users.getFirst().getNickname());
        assertEquals(List.of("1"), users.getFirst().getDeptIds());
        assertEquals(List.of("技术部"), users.getFirst().getDeptNames());
    }

    @Test
    void resolveUsersKeepsExactIdMatches() {
        PluginUserOption user = new PluginUserOption("9", "alice", "Alice", null, null, "ACTIVE", List.of(), List.of());
        PluginUserCatalogAppService service = new PluginUserCatalogAppService(
                new StubUserService(List.of(user), List.of()),
                new StubRoleManageAppService(List.of()));

        List<PluginUserCatalogDTO> users = service.resolveUsers(List.of("9", " ", "8"));

        assertEquals(1, users.size());
        assertEquals("9", users.getFirst().getId());
    }

    @Test
    void flattensDepartmentPathLabels() {
        PluginDeptOption child = new PluginDeptOption("2", "平台组", "1", "ACTIVE", List.of());
        PluginDeptOption root = new PluginDeptOption("1", "技术部", null, "ACTIVE", List.of(child));
        PluginUserCatalogAppService service = new PluginUserCatalogAppService(
                new StubUserService(List.of(), List.of(root)),
                new StubRoleManageAppService(List.of()));

        List<PluginDeptCatalogDTO> tree = service.departments(null);
        List<PluginDeptCatalogDTO> flat = service.flattenDepartments(null);

        assertEquals("技术部", tree.getFirst().getLabel());
        assertEquals("技术部 / 平台组", tree.getFirst().getChildren().getFirst().getLabel());
        assertEquals(2, flat.size());
        assertEquals("技术部", flat.getFirst().getLabel());
        assertEquals("技术部 / 平台组", flat.get(1).getLabel());
        assertTrue(flat.get(1).getChildren().isEmpty());
    }

    @Test
    void stringifiesRoleIds() {
        OptionDTO option = OptionDTO.builder()
                .id(11L)
                .label("管理员")
                .value("admin")
                .deptId(3L)
                .deptName("技术部")
                .build();
        PluginUserCatalogAppService service = new PluginUserCatalogAppService(
                new StubUserService(List.of(), List.of()),
                new StubRoleManageAppService(List.of(option)));

        List<PluginRoleCatalogDTO> roles = service.roles();

        assertEquals(1, roles.size());
        assertEquals("11", roles.getFirst().getId());
        assertEquals("admin", roles.getFirst().getCode());
        assertEquals("管理员", roles.getFirst().getName());
        assertEquals("3", roles.getFirst().getDeptId());
        assertEquals("技术部", roles.getFirst().getDeptName());
    }

    private static final class StubRoleManageAppService extends RoleManageAppService {
        private final List<OptionDTO> options;

        private StubRoleManageAppService(List<OptionDTO> options) {
            super(null, null, null, null);
            this.options = options;
        }

        @Override
        public List<OptionDTO> options() {
            return options;
        }
    }

    private static final class StubUserService implements PluginUserService {
        private final List<PluginUserOption> users;
        private final List<PluginDeptOption> departments;
        private final AtomicInteger searchCalls = new AtomicInteger();

        private StubUserService(List<PluginUserOption> users, List<PluginDeptOption> departments) {
            this.users = users;
            this.departments = departments;
        }

        @Override
        public Optional<PluginUserProfile> authenticate(String usernameOrEmail, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PluginUserProfile create(PluginUserCreate create) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PluginUserProfile> findById(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PluginUserProfile> findByUsername(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PluginUserProfile> findByEmail(String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PluginUserProfile> findByQq(String qq) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void bindQqOnce(Long userId, String qq) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PluginUserOption> searchUsers(String keyword, Long deptId, int page, int size) {
            searchCalls.incrementAndGet();
            return users;
        }

        @Override
        public List<PluginDeptOption> listDepartments(String keyword) {
            return departments;
        }

        @Override
        public List<PluginUserRole> listRoles(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PluginUserDept> listDepartments(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateProfile(Long userId, PluginUserProfileUpdate update) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PluginUserTag> listTags(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replaceTags(Long userId, String namespace, List<PluginUserTag> tags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replaceFields(Long userId, String namespace, List<PluginUserField> fields) {
            throw new UnsupportedOperationException();
        }
    }
}
