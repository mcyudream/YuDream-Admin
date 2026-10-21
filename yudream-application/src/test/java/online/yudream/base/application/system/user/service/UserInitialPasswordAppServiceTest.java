package online.yudream.base.application.system.user.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.InitialPasswordSetupTokenProvider;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import online.yudream.base.domain.system.user.valobj.InitialPasswordSetupTarget;
import online.yudream.base.domain.valobj.Email;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInitialPasswordAppServiceTest {

    private static final long USER_ID = 9007199254740993L;
    private static final String EMAIL = "imported@site.com";

    @Test
    void issuesInitialSetupMailForActiveUserWithValidEmail() {
        Fixture fixture = new Fixture(user());
        fixture.service().sendInitialPasswordSetupEmail(USER_ID);
        assertEquals(1, fixture.initialTokens.size());
        assertEquals(USER_ID, fixture.initialTokens.get(0).userId());
        assertEquals(EMAIL, fixture.initialTokens.get(0).email());
        assertEquals(1, fixture.setupMails.size());
        assertEquals("imported", fixture.setupMails.get(0).username());
        assertEquals(EMAIL, fixture.setupMails.get(0).email());
        // token 只落 token 提供者与邮件参数，不得落日志（此处日志只含 id/username）。
        assertNotEquals(fixture.setupMails.get(0).token(), EMAIL);
        assertEquals(0, fixture.passwordResetMails);
    }

    @Test
    void reissueGeneratesNewIndependentToken() {
        Fixture fixture = new Fixture(user());
        fixture.service().sendInitialPasswordSetupEmail(USER_ID);
        fixture.service().sendInitialPasswordSetupEmail(USER_ID);
        assertEquals(2, fixture.setupMails.size());
        assertNotEquals(fixture.setupMails.get(0).token(), fixture.setupMails.get(1).token());
    }

    @Test
    void rejectsMissingUser() {
        assertTrue(assertThrows(BizException.class, () -> new Fixture(user()).service()
                .sendInitialPasswordSetupEmail(42L)).getMessage().contains("用户不存在"));
    }

    @Test
    void rejectsDisabledUser() {
        User disabled = User.builder().id(USER_ID).username("imported")
                .email(Email.of(EMAIL)).status(UserStatus.DISABLED).build();
        assertTrue(assertThrows(BizException.class, () -> new Fixture(disabled).service()
                .sendInitialPasswordSetupEmail(USER_ID)).getMessage().contains("用户已停用"));
        assertTrue(new Fixture(disabled).initialTokens.isEmpty());
    }

    @Test
    void rejectsUserWithoutEmail() {
        User noEmail = User.builder().id(USER_ID).username("imported").status(UserStatus.ACTIVE).build();
        assertTrue(assertThrows(BizException.class, () -> new Fixture(noEmail).service()
                .sendInitialPasswordSetupEmail(USER_ID)).getMessage().contains("未设置邮箱"));
        assertTrue(new Fixture(noEmail).initialTokens.isEmpty());
    }

    @Test
    void rejectsEmailTakenByAnotherVerifiedUser() {
        Fixture fixture = new Fixture(user());
        fixture.sameEmailUsers.add(User.builder().id(77L).email(Email.of(EMAIL)).emailVerified(true).build());
        assertTrue(assertThrows(BizException.class, () -> fixture.service()
                .sendInitialPasswordSetupEmail(USER_ID)).getMessage().contains("邮箱已被验证用户使用"));
        assertTrue(fixture.initialTokens.isEmpty());
    }

    @Test
    void allowsSelfAndUnverifiedSameEmailAccounts() {
        Fixture fixture = new Fixture(user());
        fixture.sameEmailUsers.add(User.builder().id(77L).email(Email.of(EMAIL)).emailVerified(false).build());
        fixture.service().sendInitialPasswordSetupEmail(USER_ID);
        assertEquals(1, fixture.setupMails.size());
    }

    private static User user() {
        return User.builder().id(USER_ID).username("imported")
                .email(Email.of(EMAIL)).emailVerified(false).status(UserStatus.ACTIVE).build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, ThrowingBiFunction<String, Object[], Object> action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> action.apply(method.getName(), args));
    }

    private interface ThrowingBiFunction<A, B, R> {
        R apply(String name, Object[] args) throws Throwable;
    }

    private record SetupMail(String username, String email, String token) {
    }

    private record InitialToken(Long userId, String email) {
    }

    private static class Fixture {
        final Map<Long, User> users = new HashMap<>();
        final List<User> sameEmailUsers = new ArrayList<>();
        final AtomicInteger tokenCounter = new AtomicInteger();
        final List<InitialToken> initialTokens = new ArrayList<>();
        final List<SetupMail> setupMails = new ArrayList<>();
        int passwordResetMails;

        Fixture(User target) {
            users.put(target.getId(), target);
        }

        UserInitialPasswordAppService service() {
            UserRepo userRepo = stub(UserRepo.class, (method, args) -> switch (method) {
                case "findById" -> Optional.ofNullable(users.get((Long) args[0]));
                case "findByEmailAll" -> sameEmailUsers.stream()
                        .filter(user -> user.getEmail() != null && user.getEmail().getValue().equalsIgnoreCase((String) args[0]))
                        .toList();
                default -> throw new AssertionError(method);
            });
            InitialPasswordSetupTokenProvider tokens = stub(InitialPasswordSetupTokenProvider.class, (method, args) -> switch (method) {
                case "generate" -> {
                    initialTokens.add(new InitialToken((Long) args[0], (String) args[1]));
                    yield "initial-token-" + tokenCounter.incrementAndGet();
                }
                case "validate" -> Optional.empty();
                case "remove" -> null;
                default -> throw new AssertionError(method);
            });
            UserRegisterMailSender mailer = stub(UserRegisterMailSender.class, (method, args) -> switch (method) {
                case "sendInitialPasswordSetupEmail" -> {
                    setupMails.add(new SetupMail((String) args[0], (String) args[1], (String) args[2]));
                    yield null;
                }
                case "sendPasswordResetEmail" -> {
                    passwordResetMails++;
                    yield null;
                }
                default -> throw new AssertionError(method);
            });
            return new UserInitialPasswordAppService(userRepo, tokens, mailer);
        }
    }
}
