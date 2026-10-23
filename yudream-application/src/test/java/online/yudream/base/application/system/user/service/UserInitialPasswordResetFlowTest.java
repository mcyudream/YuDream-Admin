package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.user.cmd.UserPasswordResetCmd;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.EmailVerifyTokenProvider;
import online.yudream.base.domain.system.user.service.InitialPasswordSetupTokenProvider;
import online.yudream.base.domain.system.user.service.PasswordResetTokenProvider;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import online.yudream.base.domain.system.user.valobj.InitialPasswordSetupTarget;
import online.yudream.base.domain.system.user.valobj.PasswordResetTarget;
import online.yudream.base.domain.valobj.Email;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一密码设置入口的双 token 行为：
 * 普通密码重置行为保持不变（要求邮箱已验证）；初始密码设置 token 面向
 * 邮箱未验证的导入新用户，成功后验证邮箱并一次性消费 token。
 */
class UserInitialPasswordResetFlowTest {

    private static final long RESET_USER_ID = 5L;
    private static final long INITIAL_USER_ID = 11L;
    private static final String RESET_TOKEN = "reset-token-value";
    private static final String INITIAL_TOKEN = "initial-token-value";
    private static final String NEW_PASSWORD = "Abc98765";

    @Test
    void normalResetTokenKeepsRequiringVerifiedEmail() {
        Fixture fixture = new Fixture();
        fixture.users.put(RESET_USER_ID, user(RESET_USER_ID, "old@site.com", false, UserStatus.ACTIVE));

        BizException unverified = assertThrows(BizException.class, () -> fixture.service()
                .resetPassword(resetCmd(RESET_TOKEN)));
        assertEquals("邮箱未验证，无法重置密码", unverified.getMessage());
        assertEquals(0, fixture.saves);
        assertEquals(0, fixture.consumedResetTokens.size());
        assertEquals(0, fixture.consumedInitialTokens.size());

        fixture.users.put(RESET_USER_ID, user(RESET_USER_ID, "old@site.com", true, UserStatus.ACTIVE));
        fixture.service().resetPassword(resetCmd(RESET_TOKEN));
        assertEquals(1, fixture.saves);
        assertEquals("enc:Abc98765", fixture.users.get(RESET_USER_ID).getPassword().getEncodedPassword());
        assertTrue(fixture.consumedResetTokens.contains(RESET_TOKEN));
        assertTrue(fixture.consumedInitialTokens.isEmpty());
    }

    @Test
    void normalResetTokenStillRejectsDisabledUser() {
        Fixture fixture = new Fixture();
        fixture.users.put(RESET_USER_ID, user(RESET_USER_ID, "old@site.com", true, UserStatus.DISABLED));
        assertEquals("用户已停用", assertThrows(BizException.class, () -> fixture.service()
                .resetPassword(resetCmd(RESET_TOKEN))).getMessage());
        assertEquals(0, fixture.saves);
    }

    @Test
    void initialTokenSetsPasswordAndVerifiesEmailWithoutPriorVerification() {
        Fixture fixture = new Fixture();
        User target = user(INITIAL_USER_ID, "fresh@site.com", false, UserStatus.ACTIVE);
        fixture.users.put(INITIAL_USER_ID, target);
        User residue = user(77L, "fresh@site.com", false, UserStatus.ACTIVE);
        fixture.sameEmailUsers = List.of(target, residue);

        fixture.service().resetPassword(resetCmd(INITIAL_TOKEN));

        assertEquals(1, fixture.saves);
        assertTrue(target.isEmailVerified());
        assertEquals("enc:Abc98765", target.getPassword().getEncodedPassword());
        assertTrue(fixture.consumedInitialTokens.contains(INITIAL_TOKEN));
        assertTrue(fixture.consumedResetTokens.isEmpty());
        assertEquals(List.of(77L), fixture.deletedIds);
    }

    @Test
    void initialTokenWorksForVerifiedUserAndPurgesResidue() {
        Fixture fixture = new Fixture();
        User target = user(INITIAL_USER_ID, "fresh@site.com", true, UserStatus.ACTIVE);
        fixture.users.put(INITIAL_USER_ID, target);
        fixture.sameEmailUsers = List.of(target);
        fixture.service().resetPassword(resetCmd(INITIAL_TOKEN));
        assertTrue(target.isEmailVerified());
        assertTrue(fixture.deletedIds.isEmpty());
    }

    @Test
    void initialTokenRejectsDisabledUserEmailMismatchAndEmailConflict() {
        Fixture fixture = new Fixture();
        fixture.users.put(INITIAL_USER_ID, user(INITIAL_USER_ID, "fresh@site.com", false, UserStatus.DISABLED));
        assertEquals("用户已停用", assertThrows(BizException.class, () -> fixture.service()
                .resetPassword(resetCmd(INITIAL_TOKEN))).getMessage());

        fixture.users.put(INITIAL_USER_ID, user(INITIAL_USER_ID, "changed@site.com", false, UserStatus.ACTIVE));
        assertEquals("重置链接已失效，请重新发送初始化邮件", assertThrows(BizException.class, () -> fixture.service()
                .resetPassword(resetCmd(INITIAL_TOKEN))).getMessage());

        fixture.users.put(INITIAL_USER_ID, user(INITIAL_USER_ID, "fresh@site.com", false, UserStatus.ACTIVE));
        User verifiedOther = user(88L, "fresh@site.com", true, UserStatus.ACTIVE);
        fixture.sameEmailUsers = List.of(fixture.users.get(INITIAL_USER_ID), verifiedOther);
        assertEquals("邮箱已被验证用户使用", assertThrows(BizException.class, () -> fixture.service()
                .resetPassword(resetCmd(INITIAL_TOKEN))).getMessage());
        assertEquals(0, fixture.saves);
        assertTrue(fixture.consumedInitialTokens.isEmpty());
    }

    @Test
    void unknownOrBlankTokenStillFailsAdditionalAndConsumesNothing() {
        Fixture fixture = new Fixture();
        assertEquals("重置链接已过期或无效", assertThrows(BizException.class, () -> fixture.service()
                .resetPassword(resetCmd("bogus-token"))).getMessage());
        UserPasswordResetCmd blank = new UserPasswordResetCmd();
        blank.setToken(" ");
        assertEquals("重置链接已过期或无效", assertThrows(BizException.class, () -> fixture.service()
                .resetPassword(blank)).getMessage());
        assertTrue(fixture.consumedInitialTokens.isEmpty());
        assertTrue(fixture.consumedResetTokens.isEmpty());
        assertFalse(fixture.mailSent);
    }

    private static UserPasswordResetCmd resetCmd(String token) {
        UserPasswordResetCmd cmd = new UserPasswordResetCmd();
        cmd.setToken(token);
        cmd.setPassword(NEW_PASSWORD);
        return cmd;
    }

    private static User user(long id, String email, boolean verified, UserStatus status) {
        return User.builder().id(id).username("user-" + id).email(Email.of(email))
                .emailVerified(verified).status(status).build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, ThrowingBiFunction<String, Object[], Object> action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> action.apply(method.getName(), args));
    }

    private interface ThrowingBiFunction<A, B, R> {
        R apply(String name, Object[] args) throws Throwable;
    }

    private static class Fixture {
        final Map<Long, User> users = new HashMap<>();
        List<User> sameEmailUsers = List.of();
        final Set<String> consumedResetTokens = new java.util.HashSet<>();
        final Set<String> consumedInitialTokens = new java.util.HashSet<>();
        final List<Long> deletedIds = new ArrayList<>();
        boolean mailSent;
        int saves;

        UserAppService service() {
            UserRepo userRepo = stub(UserRepo.class, (method, args) -> switch (method) {
                case "findById" -> Optional.ofNullable(users.get(args[0]));
                case "findByEmailAll" -> sameEmailUsers;
                case "save" -> {
                    User saved = (User) args[0];
                    users.put(saved.getId(), saved);
                    saves++;
                    yield saved;
                }
                case "deleteByIds" -> {
                    deletedIds.addAll((List<Long>) args[0]);
                    yield null;
                }
                default -> throw new AssertionError(method);
            });
            PasswordResetTokenProvider resetTokens = stub(PasswordResetTokenProvider.class, (method, args) -> switch (method) {
                case "generate" -> "unused";
                case "validate" -> RESET_TOKEN.equals(args[0])
                        ? Optional.of(new PasswordResetTarget(RESET_USER_ID, "old@site.com"))
                        : Optional.empty();
                case "remove" -> {
                    consumedResetTokens.add((String) args[0]);
                    yield null;
                }
                default -> throw new AssertionError(method);
            });
            InitialPasswordSetupTokenProvider initialTokens = stub(InitialPasswordSetupTokenProvider.class, (method, args) -> switch (method) {
                case "generate" -> "unused";
                case "validate" -> INITIAL_TOKEN.equals(args[0])
                        ? Optional.of(new InitialPasswordSetupTarget(INITIAL_USER_ID, "fresh@site.com"))
                        : Optional.empty();
                case "remove" -> {
                    consumedInitialTokens.add((String) args[0]);
                    yield null;
                }
                default -> throw new AssertionError(method);
            });
            online.yudream.base.domain.common.service.PasswordEncoder encoder =
                    stub(online.yudream.base.domain.common.service.PasswordEncoder.class, (method, args) -> switch (method) {
                        case "encode" -> "enc:" + args[0];
                        case "matches" -> false;
                        default -> throw new AssertionError(method);
                    });
            EmailVerifyTokenProvider emailVerifyTokens = stub(EmailVerifyTokenProvider.class, (method, args) -> switch (method) {
                case "validate" -> Optional.empty();
                case "generate" -> "unused";
                case "remove" -> null;
                default -> throw new AssertionError(method);
            });
            UserRegisterMailSender mailer = stub(UserRegisterMailSender.class, (method, args) -> {
                mailSent = true;
                return null;
            });
            return new UserAppService(userRepo, null, null, null, null, null, null, encoder,
                    emailVerifyTokens, resetTokens, mailer, null, null, null, initialTokens, null);
        }
    }
}
