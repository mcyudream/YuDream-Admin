package online.yudream.base.application.system.user.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.system.user.dto.MessagingBindingCodeDTO;
import online.yudream.base.application.system.user.dto.MessagingBindingTargetDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MessagingBotNameLookup;
import online.yudream.base.domain.system.user.aggregate.MessagingIdentity;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.MessagingIdentityRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.MessagingIdentityClassifier;
import online.yudream.base.domain.valobj.QQ;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingCode;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingIdentityAppServiceTest {

    @Test
    void milkyBindWritesQqAndIdentityRow() {
        InMemoryUsers users = new InMemoryUsers(user(9L, null));
        InMemoryIdentities identities = new InMemoryIdentities();
        MessagingIdentityAppService service = service(identities, users);

        MessagingIdentity bound = service.bindOnce(9L, MessagingIdentityClassifier.classifyStoredQq("10086"), 1L);

        assertEquals("10086", bound.getIdentity());
        assertEquals("10086", users.findById(9L).orElseThrow().getQq().getValue());
        assertEquals(1, identities.rows.size());
    }

    @Test
    void officialBindDoesNotOverwriteUserQq() {
        InMemoryUsers users = new InMemoryUsers(user(9L, "10086"));
        InMemoryIdentities identities = new InMemoryIdentities();
        MessagingIdentityAppService service = service(identities, users);

        MessagingIdentity bound = service.bindOnce(9L, MessagingIdentityClassifier.classifyEvent(
                MilkyConnectionProtocol.OFFICIAL, "friend", "user-open", null, "app-1"), 8L);

        assertEquals(MessagingIdentityType.USER_OPENID, bound.getIdentityType());
        assertEquals("10086", users.findById(9L).orElseThrow().getQq().getValue());
    }

    @Test
    void officialConnectionKeepsOneOpenidAndOverwritesExtras() {
        InMemoryUsers users = new InMemoryUsers(user(9L, null));
        InMemoryIdentities identities = new InMemoryIdentities();
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.OFFICIAL, 8L, "app-1",
                MessagingIdentityType.USER_OPENID, "user-open", null));
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.OFFICIAL, 8L, "app-1",
                MessagingIdentityType.MEMBER_OPENID, "user-open", "group-a"));
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.OFFICIAL, 8L, "app-1",
                MessagingIdentityType.MEMBER_OPENID, "user-open", "group-b"));
        MessagingIdentityAppService service = service(identities, users);

        List<MessagingIdentity> remaining = service.listByUser(9L);
        assertEquals(1, remaining.size());
        assertEquals(MessagingIdentityType.USER_OPENID, remaining.getFirst().getIdentityType());

        MessagingIdentity rebound = service.bindOnce(9L, MessagingIdentityClassifier.classifyEvent(
                MilkyConnectionProtocol.OFFICIAL, "group", "member-open", "group-c", "app-1"), 8L);
        assertEquals("member-open", rebound.getIdentity());
        List<MessagingIdentity> afterBind = identities.findByUserId(9L);
        assertEquals(1, afterBind.size());
        assertEquals("member-open", afterBind.getFirst().getIdentity());
        assertEquals("group-c", afterBind.getFirst().getGroupOpenid());
    }

    @Test
    void officialGroupLookupUsesConnectionOpenidAfterCompact() {
        InMemoryUsers users = new InMemoryUsers(user(9L, null));
        InMemoryIdentities identities = new InMemoryIdentities();
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.OFFICIAL, 8L, "app-1",
                MessagingIdentityType.USER_OPENID, "user-open", null));
        MessagingIdentityAppService service = service(identities, users);
        MilkyConnection connection = officialConnection();

        assertTrue(service.findUserByEvent(connection, "group", "user-open", "group-open").isPresent());
        assertTrue(service.findUserByEvent(connection, "friend", "user-open", null).isPresent());
    }

    @Test
    void legacyOfficialOpenidWithoutAppIdStillMatches() {
        InMemoryUsers users = new InMemoryUsers(user(9L, null));
        InMemoryIdentities identities = new InMemoryIdentities();
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.OFFICIAL, null, null,
                MessagingIdentityType.USER_OPENID, "legacy-open", null));
        MessagingIdentityAppService service = service(identities, users);

        assertEquals(9L, service.findUserByEvent(officialConnection(), "friend", "legacy-open", null)
                .orElseThrow().getId());
    }

    @Test
    void privatePeerUsesOfficialUserOpenid() {
        InMemoryUsers users = new InMemoryUsers(user(9L, "10086"));
        InMemoryIdentities identities = new InMemoryIdentities();
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.OFFICIAL, 8L, "app-1",
                MessagingIdentityType.USER_OPENID, "user-open", null));
        MessagingIdentityAppService service = service(identities, users);

        assertEquals("user-open", service.privatePeerId(9L, officialConnection()).orElseThrow());
        assertEquals("10086", service.privatePeerId(9L, milkyConnection()).orElseThrow());
    }

    @Test
    void bindRejectsIdentityOwnedByAnotherUser() {
        InMemoryUsers users = new InMemoryUsers(user(1L, null), user(2L, null));
        InMemoryIdentities identities = new InMemoryIdentities();
        identities.save(MessagingIdentity.bind(1L, MilkyConnectionProtocol.MILKY, null, null,
                MessagingIdentityType.QQ, "10086", null));
        MessagingIdentityAppService service = service(identities, users);

        assertThrows(BizException.class, () -> service.bindOnce(2L,
                MessagingIdentityClassifier.classifyStoredQq("10086"), null));
    }

    @Test
    void listsOneBindingTargetPerEnabledConnection() {
        InMemoryUsers users = new InMemoryUsers(user(9L, "10086"));
        InMemoryIdentities identities = new InMemoryIdentities();
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.MILKY, 1L, null,
                MessagingIdentityType.QQ, "10086", null));
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.OFFICIAL, 8L, "app-1",
                MessagingIdentityType.USER_OPENID, "user-open", null));
        InMemoryConnections connections = new InMemoryConnections(milkyConnection(), officialConnection());
        MessagingIdentityAppService service = service(identities, users, connections,
                new ToggleCapability(true), new StubBindings(), Map.of(8L, "梦璃"));

        List<MessagingBindingTargetDTO> targets = service.listBindingTargets(9L);

        assertEquals(2, targets.size());
        assertEquals("1", targets.get(0).getConnectionId());
        assertEquals("本地", targets.get(0).getName());
        assertTrue(targets.get(0).isBound());
        assertEquals("8", targets.get(1).getConnectionId());
        assertEquals("梦璃", targets.get(1).getBotName());
        assertTrue(targets.get(1).isBound());
    }

    @Test
    void listsEmptyWhenMessagingCapabilityDisabled() {
        MessagingIdentityAppService service = service(new InMemoryIdentities(), new InMemoryUsers(user(9L, null)),
                new InMemoryConnections(milkyConnection()), new ToggleCapability(false), new StubBindings(), Map.of());

        assertTrue(service.listBindingTargets(9L).isEmpty());
        BizException thrown = assertThrows(BizException.class, () -> service.issueBindingCode(9L, "1"));
        assertEquals("QQ 消息平台未启用", thrown.getMessage());
    }

    @Test
    void issueBindingCodeRejectsMissingAndDisabledConnection() {
        MilkyConnection disabled = officialConnection();
        disabled.setEnabled(false);
        InMemoryUsers users = new InMemoryUsers(user(9L, null));
        MessagingIdentityAppService service = service(new InMemoryIdentities(), users,
                new InMemoryConnections(milkyConnection(), disabled),
                new ToggleCapability(true), new StubBindings(), Map.of(1L, "本地Bot"));

        assertEquals("请选择要绑定的机器人连接",
                assertThrows(BizException.class, () -> service.issueBindingCode(9L, " ")).getMessage());
        assertEquals("消息连接不存在",
                assertThrows(BizException.class, () -> service.issueBindingCode(9L, "99")).getMessage());
        assertEquals("该机器人连接未启用",
                assertThrows(BizException.class, () -> service.issueBindingCode(9L, "8")).getMessage());

        MessagingBindingCodeDTO issued = service.issueBindingCode(9L, "1");
        assertEquals("123456", issued.getCode());
        assertEquals("1", issued.getConnectionId());
        assertEquals("本地", issued.getConnectionName());
        assertEquals("本地Bot", issued.getBotName());
    }

    @Test
    void milkyConnectionIsBoundByAnyQqIdentity() {
        InMemoryUsers users = new InMemoryUsers(user(9L, "10086"));
        InMemoryIdentities identities = new InMemoryIdentities();
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.MILKY, null, null,
                MessagingIdentityType.QQ, "10086", null));
        MessagingIdentityAppService service = service(identities, users, new InMemoryConnections(milkyConnection()),
                new ToggleCapability(true), new StubBindings(), Map.of());

        assertTrue(service.listBindingTargets(9L).getFirst().isBound());
    }

    @Test
    void officialConnectionIsNotBoundByMilkyQq() {
        InMemoryUsers users = new InMemoryUsers(user(9L, "10086"));
        InMemoryIdentities identities = new InMemoryIdentities();
        identities.save(MessagingIdentity.bind(9L, MilkyConnectionProtocol.MILKY, 1L, null,
                MessagingIdentityType.QQ, "10086", null));
        MessagingIdentityAppService service = service(identities, users, new InMemoryConnections(officialConnection()),
                new ToggleCapability(true), new StubBindings(), Map.of());

        assertFalse(service.listBindingTargets(9L).getFirst().isBound());
    }

    private static MessagingIdentityAppService service(InMemoryIdentities identities, InMemoryUsers users) {
        return service(identities, users, new EmptyConnections(), new ToggleCapability(true),
                new StubBindings(), Map.of());
    }

    private static MessagingIdentityAppService service(InMemoryIdentities identities, InMemoryUsers users,
                                                       MilkyConnectionRepo connections, CapabilityAppService capability,
                                                       PluginQqBindingService bindings, Map<Long, String> botNames) {
        return new MessagingIdentityAppService(identities, users, connections, capability, bindings,
                connectionId -> Optional.ofNullable(botNames.get(connectionId)));
    }

    private static User user(Long id, String qq) {
        User user = new User();
        user.setId(id);
        user.setUsername("u" + id);
        user.setStatus(UserStatus.ACTIVE);
        if (qq != null) {
            user.setQq(QQ.of(qq));
        }
        return user;
    }

    private static MilkyConnection officialConnection() {
        MilkyConnection connection = MilkyConnection.create("官方", "official", null, null,
                "app-1", "secret", false, null, "base64", null);
        connection.setId(8L);
        return connection;
    }

    private static MilkyConnection milkyConnection() {
        MilkyConnection connection = MilkyConnection.create("本地", "http://127.0.0.1:3010", "token", "base64", null);
        connection.setId(1L);
        return connection;
    }

    private static final class InMemoryUsers implements UserRepo {
        private final List<User> users;

        private InMemoryUsers(User... users) {
            this.users = new ArrayList<>(List.of(users));
        }

        @Override
        public User save(User user) {
            users.removeIf(item -> item.getId().equals(user.getId()));
            users.add(user);
            return user;
        }

        @Override
        public boolean existsByUsername(String username) {
            return false;
        }

        @Override
        public boolean existsVerifiedByUsername(String username) {
            return false;
        }

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public boolean existsVerifiedByEmail(String email) {
            return false;
        }

        @Override
        public boolean existsByQQ(String qq) {
            return findByQQ(qq).isPresent();
        }

        @Override
        public boolean existsByPhone(String phone) {
            return false;
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public List<User> findByUsernameAll(String username) {
            return List.of();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByQQ(String qq) {
            return users.stream()
                    .filter(user -> user.getQq() != null && qq.equals(user.getQq().getValue()))
                    .findFirst();
        }

        @Override
        public List<User> findAllWithQq() {
            return users.stream().filter(user -> user.getQq() != null).toList();
        }

        @Override
        public List<User> findByEmailAll(String email) {
            return List.of();
        }

        @Override
        public Optional<User> findById(Long id) {
            return users.stream().filter(user -> user.getId().equals(id)).findFirst();
        }

        @Override
        public List<User> findByIds(List<Long> ids) {
            return List.of();
        }

        @Override
        public void deleteByIds(List<Long> ids) {
        }

        @Override
        public PageResult<User> page(String keyword, Long deptId, Long roleId, Boolean emailVerified, UserStatus status, int page, int size) {
            return new PageResult<>(List.of(), 0, page, size);
        }

        @Override
        public boolean existsByUsernameExcludeId(String username, Long excludeId) {
            return false;
        }

        @Override
        public boolean existsByEmailExcludeId(String email, Long excludeId) {
            return false;
        }

        @Override
        public boolean existsByPhoneExcludeId(String phone, Long excludeId) {
            return false;
        }

        @Override
        public boolean existsByQQExcludeId(String qq, Long excludeId) {
            return users.stream()
                    .anyMatch(user -> user.getQq() != null && qq.equals(user.getQq().getValue())
                            && !user.getId().equals(excludeId));
        }

        @Override
        public long countByRoleId(Long roleId) {
            return 0;
        }

        @Override
        public long countByDeptId(Long deptId) {
            return 0;
        }
    }

    private static final class InMemoryIdentities implements MessagingIdentityRepo {
        private final List<MessagingIdentity> rows = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong(1);

        @Override
        public MessagingIdentity save(MessagingIdentity identity) {
            if (identity.getId() == null) {
                identity.setId(ids.getAndIncrement());
            }
            rows.removeIf(item -> item.getId().equals(identity.getId()));
            rows.add(identity);
            return identity;
        }

        @Override
        public Optional<MessagingIdentity> findById(Long id) {
            return rows.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public List<MessagingIdentity> findByUserId(Long userId) {
            return rows.stream().filter(item -> item.getUserId().equals(userId)).toList();
        }

        @Override
        public List<MessagingIdentity> findByUserIds(List<Long> userIds) {
            if (userIds == null || userIds.isEmpty()) {
                return List.of();
            }
            return rows.stream().filter(item -> userIds.contains(item.getUserId())).toList();
        }

        @Override
        public Optional<MessagingIdentity> findMilkyQq(String identity) {
            return rows.stream()
                    .filter(item -> item.getIdentityType() == MessagingIdentityType.QQ && item.getIdentity().equals(identity))
                    .findFirst();
        }

        @Override
        public Optional<MessagingIdentity> findOfficialUserOpenid(String appId, String identity) {
            return rows.stream()
                    .filter(item -> item.getIdentityType() == MessagingIdentityType.USER_OPENID
                            && item.getIdentity().equals(identity)
                            && ((appId == null && item.getAppId() == null)
                            || (appId != null && (item.getAppId() == null || appId.equals(item.getAppId())))))
                    .findFirst();
        }

        @Override
        public Optional<MessagingIdentity> findOfficialMemberOpenid(String appId, String groupOpenid, String identity) {
            return rows.stream()
                    .filter(item -> item.getIdentityType() == MessagingIdentityType.MEMBER_OPENID
                            && item.getIdentity().equals(identity)
                            && groupOpenid.equals(item.getGroupOpenid())
                            && ((appId == null && item.getAppId() == null)
                            || (appId != null && (item.getAppId() == null || appId.equals(item.getAppId())))))
                    .findFirst();
        }

        @Override
        public List<MessagingIdentity> findByIdentity(String identity) {
            return rows.stream().filter(item -> item.getIdentity().equals(identity)).toList();
        }

        @Override
        public Optional<MessagingIdentity> findExact(MilkyConnectionProtocol protocol, MessagingIdentityType identityType,
                                                     String appId, String groupOpenid, String identity) {
            return switch (identityType) {
                case QQ -> findMilkyQq(identity);
                case USER_OPENID -> findOfficialUserOpenid(appId, identity);
                case MEMBER_OPENID -> findOfficialMemberOpenid(appId, groupOpenid, identity);
            };
        }

        @Override
        public void deleteById(Long id) {
            rows.removeIf(item -> item.getId().equals(id));
        }

        @Override
        public void deleteMilkyQqByUserId(Long userId) {
            rows.removeIf(item -> item.getUserId().equals(userId) && item.getIdentityType() == MessagingIdentityType.QQ);
        }
    }

    private static final class EmptyConnections implements MilkyConnectionRepo {
        @Override
        public MilkyConnection save(MilkyConnection connection) {
            return connection;
        }

        @Override
        public Optional<MilkyConnection> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<MilkyConnection> findEnabled() {
            return List.of();
        }

        @Override
        public PageResult<MilkyConnection> page(String keyword, int page, int size) {
            return new PageResult<>(List.of(), 0, page, size);
        }
    }

    private static final class InMemoryConnections implements MilkyConnectionRepo {
        private final List<MilkyConnection> connections;

        private InMemoryConnections(MilkyConnection... connections) {
            this.connections = new ArrayList<>(List.of(connections));
        }

        @Override
        public MilkyConnection save(MilkyConnection connection) {
            connections.removeIf(item -> item.getId().equals(connection.getId()));
            connections.add(connection);
            return connection;
        }

        @Override
        public Optional<MilkyConnection> findById(Long id) {
            return connections.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public List<MilkyConnection> findEnabled() {
            return connections.stream().filter(MilkyConnection::isEnabled).toList();
        }

        @Override
        public PageResult<MilkyConnection> page(String keyword, int page, int size) {
            return new PageResult<>(connections, connections.size(), page, size);
        }
    }

    private static final class ToggleCapability extends CapabilityAppService {
        private final boolean enabled;

        private ToggleCapability(boolean enabled) {
            super(null, List.of());
            this.enabled = enabled;
        }

        @Override
        public boolean enabled(String code) {
            return enabled;
        }
    }

    private static final class StubBindings implements PluginQqBindingService {
        @Override
        public PluginQqBindingCode issue(Long userId) {
            return new PluginQqBindingCode("123456", Instant.parse("2026-09-08T12:00:00Z"));
        }

        @Override
        public Long consume(String code) {
            return 9L;
        }
    }
}
