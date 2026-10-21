package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.user.cmd.TemplateUserUpsertCmd;
import online.yudream.base.application.system.user.dto.TemplateUserUpsertResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.InitialPasswordSetupTokenProvider;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateUserAppServiceTest {

    private static final long ROOT_DEPT_ID = 1L;
    private static final long USER_ROLE_ID = 99L;
    private static final long NEW_USER_ID = 101L;

    @Test
    void createsNewUserWithPlaceholderPasswordRootDeptUserRoleAndPendingInitialMail() {
        Fixture fixture = new Fixture();
        TemplateUserUpsertResult result = fixture.service().upsert(cmd(cmd -> {
            cmd.setUsername("import.1001");
            cmd.setNickname("导入人员");
            cmd.setEmail("import.1001@site.com");
        }));
        assertTrue(result.isCreated());
        assertEquals(NEW_USER_ID, result.getUserId());
        assertEquals("import.1001", result.getUsername());
        assertTrue(result.isNeedsInitialPasswordEmail());

        User saved = fixture.savedUser;
        // 占位密码：只保留加密形态，原始明文不可导出。
        assertNull(saved.getPassword().getPassword());
        assertNotNull(saved.getPassword().getEncodedPassword());
        assertTrue(((String) fixture.encodedInput.get(0)).length() >= 32);
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertFalse(saved.isEmailVerified());
        assertEquals(List.of(USER_ROLE_ID), fixture.roleIds(saved));
        assertEquals(List.of(DeptID.of(ROOT_DEPT_ID)), saved.getDepts().stream().map(UserDept::id).toList());
        assertEquals(ROOT_DEPT_ID, saved.getDefaultDeptID().getValue());
    }

    @Test
    void createRequiresEmailForSafeInitialPasswordDelivery() {
        Fixture fixture = new Fixture();

        BizException error = assertThrows(BizException.class, () -> fixture.service().upsert(cmd(c ->
                c.setUsername("import.without-email"))));

        assertTrue(error.getMessage().contains("必须提供邮箱"));
        assertNull(fixture.savedUser);
    }

    @Test
    void verifiedSameUsernameBecomesUpdateTargetInsteadOfCreateConflict() {
        Fixture fixture = new Fixture();
        fixture.allUsers.add(User.builder().id(5L).username("existing")
                .email(Email.of("other@site.com")).emailVerified(true).status(UserStatus.ACTIVE)
                .nickname("旧昵称").build());

        // 已验证同用户名 = 更新目标（身份键），不是新建冲突
        TemplateUserUpsertCmd sameName = cmd(c -> {
            c.setUsername("existing");
            c.setNickname("新昵称");
        });
        TemplateUserUpsertResult result = fixture.service().upsert(sameName);
        assertFalse(result.isCreated());
        assertEquals(5L, result.getUserId());
        assertEquals("新昵称", fixture.savedUser.getNickname());
        assertFalse(result.isNeedsInitialPasswordEmail());
    }

    @Test
    void createRejectsVerifiedEmailConflict() {
        Fixture fixture = new Fixture();
        fixture.allUsers.add(User.builder().id(5L).username("existing")
                .email(Email.of("other@site.com")).emailVerified(true).status(UserStatus.ACTIVE).build());

        TemplateUserUpsertCmd emailConflict = cmd(c -> {
            c.setUsername("import.1001");
            c.setEmail("other@site.com");
        });
        assertTrue(assertThrows(BizException.class, () -> fixture.service().upsert(emailConflict))
                .getMessage().contains("邮箱已存在"));
    }

    @Test
    void unverifiedUsernameResidueBecomesRetryUpdateTarget() {
        Fixture fixture = new Fixture();
        fixture.allUsers.add(User.builder().id(77L).username("import.1001")
                .email(Email.of("stray@site.com")).emailVerified(false).status(UserStatus.ACTIVE)
                .password(Password.fromEncoded("enc:pending")).build());
        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setUsername("import.1001");
            c.setEmail("import@site.com");
        });
        TemplateUserUpsertResult result = fixture.service().upsert(cmd);
        assertFalse(result.isCreated());
        assertFalse(result.isNeedsInitialPasswordEmail());
        assertEquals(77L, result.getUserId());
        assertEquals("enc:pending", fixture.savedUser.getPassword().getEncodedPassword());
    }

    @Test
    void createMarkedVerifiedPurgesSameEmailResidueAndStillRequestsInitialMail() {
        Fixture fixture = new Fixture();
        fixture.allUsers.add(User.builder().id(77L).username("stray")
                .email(Email.of("import@site.com")).emailVerified(false).status(UserStatus.ACTIVE).build());
        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setUsername("import.1001");
            c.setEmail("import@site.com");
            c.setEmailVerified(Boolean.TRUE);
        });
        TemplateUserUpsertResult result = fixture.service().upsert(cmd);
        assertTrue(result.isCreated());
        // 服务端不导出密码，即使声明 emailVerified=true 也要先完成初始密码设置
        assertTrue(result.isNeedsInitialPasswordEmail());
        assertTrue(fixture.savedUser.isEmailVerified());
        assertEquals(List.of(77L), fixture.deletedIds);
    }

    @Test
    void disablesAndCustomDeptsRolesAreHonoredOnCreate() {
        Fixture fixture = new Fixture();
        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setUsername("import.1001");
            c.setEmail("import.1001@site.com");
            c.setDeptIds(List.of(10L, 20L));
            c.setDefaultDeptId(20L);
            c.setRoleIds(List.of(2L));
            c.setStatus(UserStatus.DISABLED);
        });
        TemplateUserUpsertResult result = fixture.service().upsert(cmd);
        assertTrue(result.isCreated());
        assertFalse(result.isNeedsInitialPasswordEmail());
        assertEquals(UserStatus.DISABLED, fixture.savedUser.getStatus());
        assertTrue(fixture.savedUser.getDepts().stream().anyMatch(dept -> dept.id().getValue().equals(20L) && dept.isDefault()));
        assertEquals(List.of(2L), fixture.roleIds(fixture.savedUser));
    }

    @Test
    void createRejectsRoleOutsideAssignedDeptsAndDefaultOutsideDeptList() {
        Fixture fixture = new Fixture();
        TemplateUserUpsertCmd roleOutside = cmd(c -> {
            c.setUsername("import.1001");
            c.setEmail("import.1001@site.com");
            c.setDeptIds(List.of(10L));
            c.setRoleIds(List.of(3L)); // 部门 3 的角色
        });
        assertTrue(assertThrows(BizException.class, () -> fixture.service().upsert(roleOutside))
                .getMessage().contains("已分配部门"));

        TemplateUserUpsertCmd defaultOutside = cmd(c -> {
            c.setUsername("import.1001");
            c.setEmail("import.1001@site.com");
            c.setDeptIds(List.of(10L));
            c.setDefaultDeptId(30L);
        });
        assertTrue(assertThrows(BizException.class, () -> fixture.service().upsert(defaultOutside))
                .getMessage().contains("默认部门必须属于已分配部门"));
    }

    @Test
    void updateKeepsUsernameAndPasswordAndRequiresProvidedChanges() {
        Fixture fixture = new Fixture();
        User existing = User.builder().id(5L).username("existing")
                .email(Email.of("other@site.com")).emailVerified(true).status(UserStatus.ACTIVE)
                .nickname("旧昵称")
                .password(Password.fromEncoded("enc:old"))
                .depts(new ArrayList<>(List.of(new UserDept(DeptID.of(10L), true)))).build();
        fixture.verifiedUsers.add(existing);
        fixture.allUsers.add(existing);

        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setUsername("existing");
            c.setNickname("新昵称");
            c.setEmail("moved@site.com");
        });
        TemplateUserUpsertResult result = fixture.service().upsert(cmd);
        assertFalse(result.isCreated());
        assertFalse(result.isNeedsInitialPasswordEmail());
        assertEquals(5L, result.getUserId());

        User saved = fixture.savedUser;
        assertEquals("existing", saved.getUsername());
        assertEquals("enc:old", saved.getPassword().getEncodedPassword());
        assertEquals("新昵称", saved.getNickname());
        assertEquals("moved@site.com", saved.getEmail().getValue());
        assertEquals(List.of(DeptID.of(10L)), saved.getDepts().stream().map(UserDept::id).toList());
    }

    @Test
    void updateRejectsEmailConflict() {
        Fixture fixture = new Fixture();
        fixture.verifiedUsers.add(User.builder().id(5L).username("existing")
                .email(Email.of("old@site.com")).emailVerified(true).status(UserStatus.ACTIVE).build());
        fixture.otherEmailUserExists = true;
        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setUsername("existing");
            c.setEmail("taken@site.com");
        });
        assertTrue(assertThrows(BizException.class, () -> fixture.service().upsert(cmd))
                .getMessage().contains("邮箱已被使用"));
    }

    @Test
    void updateReplacesDeptsAndPrunesRolesOutsideNewDepts() {
        Fixture fixture = new Fixture();
        User existing = User.builder().id(5L).username("existing")
                .email(Email.of("old@site.com")).emailVerified(true).status(UserStatus.ACTIVE)
                .roles(new ArrayList<>(List.of(RoleID.of(2L))))
                .depts(new ArrayList<>(List.of(new UserDept(DeptID.of(10L), true)))).build();
        fixture.verifiedUsers.add(existing);
        fixture.allUsers.add(existing);

        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setUsername("existing");
            c.setDeptIds(List.of(20L));
        });
        fixture.service().upsert(cmd);
        // 角色未提供：部门替换后，不再属于新部门的既有角色被剪除。
        List<Long> nextRoles = fixture.savedUser.getRoles().stream().map(RoleID::getValue).toList();
        assertEquals(List.of(), nextRoles); // 角色 2 属于旧部门 10
    }

    @Test
    void sendInitialPasswordSetupEmailDelegatesToFocusedService() {
        Fixture fixture = new Fixture();
        fixture.allUsers.add(User.builder().id(5L).username("existing")
                .email(Email.of("old@site.com")).emailVerified(true).status(UserStatus.ACTIVE).build());
        fixture.service().sendInitialPasswordSetupEmail(5L);
        assertEquals(1, fixture.initialTokens.size());
        assertEquals(5L, fixture.initialTokens.get(0).userId());
        assertEquals("old@site.com", fixture.initialTokens.get(0).email());
    }

    @Test
    void retriesSourceIdLessUnverifiedImportedUserByUsername() {
        Fixture fixture = new Fixture();
        User pending = User.builder().id(7L).username("import.1001")
                .email(Email.of("import@site.com")).emailVerified(false).status(UserStatus.ACTIVE)
                .nickname("首次导入").password(Password.fromEncoded("enc:pending")).build();
        fixture.allUsers.add(pending);

        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setUsername("import.1001");
            c.setNickname("重试导入");
            c.setEmail("import@site.com");
        });

        TemplateUserUpsertResult result = fixture.service().upsert(cmd);

        assertFalse(result.isCreated());
        assertEquals(7L, result.getUserId());
        assertEquals("重试导入", fixture.savedUser.getNickname());
        assertEquals("enc:pending", fixture.savedUser.getPassword().getEncodedPassword());
    }

    @Test
    void sourceIdMappingTakesPriorityAndRequiresSameUsername() {
        Fixture fixture = new Fixture();
        fixture.allUsers.add(User.builder().id(77L).username("import.1001")
                .email(Email.of("stray@site.com")).emailVerified(false).status(UserStatus.ACTIVE).build());
        fixture.allUsers.add(User.builder().id(9L).username("import.1001").nickname("首导入")
                .email(Email.of("import@site.com")).emailVerified(false).status(UserStatus.ACTIVE).build());

        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setSourceId("9");
            c.setUsername("import.1001");
            c.setNickname("二导入");
        });
        TemplateUserUpsertResult result = fixture.service().upsert(cmd);
        assertFalse(result.isCreated());
        assertEquals(9L, result.getUserId());
        assertEquals("二导入", fixture.savedUser.getNickname());
    }

    @Test
    void staleSourceIdMappingFallsBackToUsernameTargetAndFailsWithoutOne() {
        // 中央口径：sourceId 指向他人账号时按 username 解析兜底，命中已验证目标即更新该目标
        Fixture fixture = new Fixture();
        fixture.allUsers.add(User.builder().id(9L).username("someone.else")
                .email(Email.of("else@site.com")).emailVerified(true).status(UserStatus.ACTIVE)
                .password(Password.fromEncoded("enc:else")).build());
        User verifiedTarget = User.builder().id(5L).username("import.1001")
                .email(Email.of("import@site.com")).emailVerified(true).status(UserStatus.ACTIVE)
                .nickname("旧昵称").password(Password.fromEncoded("enc:old")).build();
        fixture.allUsers.add(verifiedTarget);
        fixture.verifiedUsers.add(verifiedTarget);

        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setSourceId("9");
            c.setUsername("import.1001");
            c.setNickname("新昵称");
        });
        TemplateUserUpsertResult result = fixture.service().upsert(cmd);
        assertFalse(result.isCreated());
        assertEquals(5L, result.getUserId());
        assertEquals("新昵称", fixture.savedUser.getNickname());
        assertEquals("enc:old", fixture.savedUser.getPassword().getEncodedPassword());

        // sourceId 指向他人且 username 解析无业务键目标 → 安全失败，不得新建
        Fixture withoutUsernameMatch = new Fixture();
        withoutUsernameMatch.allUsers.add(User.builder().id(9L).username("someone.else")
                .email(Email.of("else@site.com")).emailVerified(true).status(UserStatus.ACTIVE).build());
        assertThrows(BizException.class, () -> withoutUsernameMatch.service()
                .upsert(cmd(c -> {
                    c.setSourceId("9");
                    c.setUsername("import.1001");
                })));
        assertTrue(withoutUsernameMatch.savedUser == null, "不得创建新账号");
    }

    @Test
    void sourceIdPointingToMissingUserStillFallsBackToUsername() {
        Fixture fixture = new Fixture();
        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setSourceId("9");
            c.setUsername("import.1001");
            c.setEmail("import@site.com");
        });
        TemplateUserUpsertResult result = fixture.service().upsert(cmd);
        assertTrue(result.isCreated());
        assertEquals(NEW_USER_ID, result.getUserId());
    }

    @Test
    void verifiedDisabledUserStillMatchesForUpsert() {
        Fixture fixture = new Fixture();
        User disabled = User.builder().id(5L).username("existing")
                .email(Email.of("old@site.com")).emailVerified(true).status(UserStatus.DISABLED)
                .nickname("旧昵称").password(Password.fromEncoded("enc:old")).build();
        fixture.allUsers.add(disabled);
        fixture.verifiedUsers.add(disabled);

        TemplateUserUpsertCmd cmd = cmd(c -> {
            c.setUsername("existing");
            c.setNickname("新昵称");
        });
        TemplateUserUpsertResult result = fixture.service().upsert(cmd);
        assertFalse(result.isCreated());
        assertEquals(5L, result.getUserId());
        assertEquals("新昵称", fixture.savedUser.getNickname());
        assertEquals("enc:old", fixture.savedUser.getPassword().getEncodedPassword());
        assertEquals(UserStatus.DISABLED, fixture.savedUser.getStatus());
    }

    private static TemplateUserUpsertCmd cmd(java.util.function.Consumer<TemplateUserUpsertCmd> shaper) {
        TemplateUserUpsertCmd cmd = new TemplateUserUpsertCmd();
        cmd.setSourceId("template-row-1");
        shaper.accept(cmd);
        return cmd;
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, ThrowingBiFunction<String, Object[], Object> action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> action.apply(method.getName(), args));
    }

    private interface ThrowingBiFunction<A, B, R> {
        R apply(String name, Object[] args) throws Throwable;
    }

    private record InitialToken(Long userId, String email) {
    }

    private static class Fixture {
        final List<User> allUsers = new ArrayList<>();
        final List<User> verifiedUsers = new ArrayList<>();
        final Map<Long, Role> roles = new HashMap<>(Map.of(
                2L, Role.builder().id(2L).deptId(DeptID.of(10L)).status(RoleStatus.ACTIVE).build(),
                3L, Role.builder().id(3L).deptId(DeptID.of(20L)).status(RoleStatus.ACTIVE).build(),
                USER_ROLE_ID, Role.builder().id(USER_ROLE_ID).deptId(DeptID.of(ROOT_DEPT_ID))
                        .systemType(SystemRoleType.USER).status(RoleStatus.ACTIVE).build()));
        final Map<Long, Dept> depts = new HashMap<>(Map.of(
                10L, Dept.builder().id(10L).build(),
                20L, Dept.builder().id(20L).build(),
                30L, Dept.builder().id(30L).build()));
        final List<InitialToken> initialTokens = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicLong idCounter = new java.util.concurrent.atomic.AtomicLong(NEW_USER_ID);
        final List<String> encodedInput = new ArrayList<>();
        final List<Long> deletedIds = new ArrayList<>();
        boolean otherEmailUserExists;
        User savedUser;
        boolean syncedMilkyQq;

        TemplateUserAppService service() {
            UserRepo userRepo = stub(UserRepo.class, (method, args) -> switch (method) {
                case "findById" -> Optional.ofNullable(allUsers.stream()
                        .filter(user -> user.getId().equals(args[0])).findFirst().orElse(null));
                case "findByUsernameAll" -> java.util.stream.Stream.concat(allUsers.stream(), verifiedUsers.stream())
                        .filter(user -> ((String) args[0]).equals(user.getUsername()))
                        .distinct().toList();
                case "findByEmailAll" -> java.util.stream.Stream.concat(allUsers.stream(), verifiedUsers.stream())
                        .filter(user -> user.getEmail() != null && user.getEmail().getValue().equalsIgnoreCase((String) args[0]))
                        .distinct().toList();
                case "existsByEmailExcludeId" -> otherEmailUserExists;
                case "existsByPhoneExcludeId" -> false;
                case "existsByQQExcludeId" -> false;
                case "save" -> {
                    User user = (User) args[0];
                    if (user.getId() == null) {
                        user.setId(idCounter.getAndIncrement());
                    }
                    savedUser = user;
                    yield user;
                }
                case "deleteByIds" -> {
                    deletedIds.addAll((List<Long>) args[0]);
                    yield null;
                }
                default -> throw new AssertionError(method);
            });
            RoleRepo roleRepo = stub(RoleRepo.class, (method, args) -> switch (method) {
                case "findByIds" -> ((List<Long>) args[0]).stream().map(roles::get).filter(java.util.Objects::nonNull).toList();
                case "findBySystemType" -> Optional.of(roles.get(USER_ROLE_ID));
                default -> throw new AssertionError(method);
            });
            DeptRepo deptRepo = stub(DeptRepo.class, (method, args) -> switch (method) {
                case "findRoot" -> Optional.of(Dept.builder().id(ROOT_DEPT_ID).build());
                case "findByIds" -> ((List<Long>) args[0]).stream().map(depts::get).filter(java.util.Objects::nonNull).toList();
                default -> throw new AssertionError(method);
            });
            online.yudream.base.domain.common.service.PasswordEncoder encoder =
                    stub(online.yudream.base.domain.common.service.PasswordEncoder.class, (method, args) -> switch (method) {
                        case "encode" -> {
                            encodedInput.add((String) args[0]);
                            yield "enc: stubbed";
                        }
                        case "matches" -> false;
                        default -> throw new AssertionError(method);
                    });
            MessagingIdentityAppService messaging = new MessagingIdentityAppService(null, null, null, null, null, null) {
                @Override
                public void syncMilkyQq(Long userId, String qq) {
                    syncedMilkyQq = true;
                }
            };
            UserInitialPasswordAppService initialPassword = new UserInitialPasswordAppService(
                    userRepo,
                    stub(InitialPasswordSetupTokenProvider.class, (method, args) -> switch (method) {
                        case "generate" -> {
                            initialTokens.add(new InitialToken((Long) args[0], (String) args[1]));
                            yield "token";
                        }
                        case "validate" -> Optional.empty();
                        case "remove" -> null;
                        default -> throw new AssertionError(method);
                    }),
                    mailer());
            return new TemplateUserAppService(userRepo, roleRepo, deptRepo, encoder, messaging, initialPassword);
        }

        private UserRegisterMailSender mailer() {
            return stub(UserRegisterMailSender.class, (method, args) -> switch (method) {
                case "sendInitialPasswordSetupEmail", "sendVerifyEmail", "sendPasswordResetEmail" -> null;
                default -> throw new AssertionError(method);
            });
        }

        List<Long> roleIds(User user) {
            return user.getRoles().stream().map(RoleID::getValue).toList();
        }
    }
}
