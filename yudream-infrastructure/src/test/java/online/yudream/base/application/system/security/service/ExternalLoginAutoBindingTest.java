package online.yudream.base.application.system.security.service;

import online.yudream.base.application.system.security.dto.ExternalLoginCallbackDTO;
import online.yudream.base.application.system.security.dto.LoginTokenDTO;
import online.yudream.base.application.system.user.dto.UserProfileDTO;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ExternalAccount;
import online.yudream.base.domain.system.security.repo.ExternalAccountRepo;
import online.yudream.base.domain.system.security.service.ExternalLoginTicketStore;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.plugin.spi.system.auth.*;
import online.yudream.base.plugin.spi.system.extension.PluginExtensionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExternalLoginAutoBindingTest {
    private final long userId = 357806992028471296L;
    private final Map<Long, User> users = new HashMap<>();
    private final Tickets tickets = new Tickets();
    private final Accounts accounts = new Accounts();
    private PluginExternalLoginIdentity identity;
    private boolean enabled = true;
    private int exchanges;
    private int issuedSessions;
    private ExternalLoginAppService app;

    @BeforeEach
    void setUp() {
        users.put(userId, User.builder().id(userId).status(UserStatus.ACTIVE).build());
        identity = identity(String.valueOf(userId));
        UserRepo userRepo = (UserRepo) Proxy.newProxyInstance(UserRepo.class.getClassLoader(),
                new Class<?>[]{UserRepo.class}, (proxy, method, args) -> {
                    if (method.getName().equals("findById")) return Optional.ofNullable(users.get(args[0]));
                    throw new AssertionError(method.getName());
                });
        UserAppService userApp = new UserAppService(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null) {
            @Override
            public UserProfileDTO profile(Long id) {
                return UserProfileDTO.builder().id(id).username("student@site.edu.cn")
                        .email("student@site.edu.cn").emailVerified(true).build();
            }
        };
        LoginTokenAppService tokens = new LoginTokenAppService(null, null, null) {
            @Override
            public LoginTokenDTO issueForLogin(Long id) {
                issuedSessions++;
                return LoginTokenDTO.builder().token("session-" + id).tokenName("Authorization").build();
            }
        };
        PluginExternalLoginProvider provider = new PluginExternalLoginProvider() {
            public PluginExternalLoginDescriptor descriptor() {
                return new PluginExternalLoginDescriptor("eduroam", "Eduroam", null, List.of("eduroam"), 0);
            }
            public boolean enabled() { return enabled; }
            public String authorizationUrl(PluginExternalLoginAuthorizeRequest request) { return "/eduroam"; }
            public PluginExternalLoginIdentity exchange(PluginExternalLoginExchangeRequest request) {
                exchanges++;
                return identity;
            }
        };
        PluginExtensionQuery extensions = new PluginExtensionQuery() {
            public <I> List<I> extensions(Class<I> type) { return List.of(type.cast(provider)); }
        };
        app = new ExternalLoginAppService(null, accounts, null, userApp, tokens, tickets,
                new ExternalLoginBindingAppService(tickets, accounts, userRepo), extensions);
        authorize(null);
    }

    @Test
    void provenNewUserIsBoundAndLoggedInOnFirstCallback() {
        var result = callback();
        assertEquals(ExternalLoginCallbackDTO.Outcome.LOGIN, result.getOutcome());
        assertEquals(userId, result.getSession().getUserId());
        assertEquals(userId, accounts.account.getUserId());
        assertEquals("student@school.edu.cn", accounts.account.getSocialUid());
        assertEquals(1, issuedSessions);
        assertTrue(tickets.bindings.isEmpty());
        assertThrows(BizException.class, this::callback);
        assertEquals(1, exchanges, "重放 state 不能再次调用插件");
    }

    @Test
    void legacyIdentityStillRequiresExplicitBinding() {
        identity = new PluginExternalLoginIdentity("student@school.edu.cn", "student", null, null, null);
        assertNull(identity.authenticatedUserId());
        assertEquals(ExternalLoginCallbackDTO.Outcome.BIND_REQUIRED, callback().getOutcome());
        assertNull(accounts.account);
        assertEquals(0, issuedSessions);
        assertEquals(1, tickets.bindings.size());
    }

    @Test
    void existingBindingContinuesToLoginWithoutAUserAssertion() {
        bindTo(userId);
        identity = identity(null);
        assertEquals(userId, callback().getSession().getUserId());
    }

    @Test
    void existingBindingCannotBeReassigned() {
        bindTo(userId + 1);
        assertThrows(BizException.class, this::callback);
        assertEquals(userId + 1, accounts.account.getUserId());
        assertEquals(0, issuedSessions);
    }

    @Test
    void disabledAndMissingUsersCannotBeAutoBound() {
        users.get(userId).disable();
        assertThrows(BizException.class, this::callback);
        authorize(null);
        users.clear();
        assertThrows(BizException.class, this::callback);
        assertNull(accounts.account);
        assertEquals(0, issuedSessions);
    }

    @Test
    void loggedInBindingIntentCannotTargetDifferentUser() {
        authorize(userId + 1);
        assertThrows(BizException.class, this::callback);
        assertNull(accounts.account);
        assertEquals(0, issuedSessions);
    }

    @Test
    void matchingLoggedInBindingIntentReturnsBound() {
        authorize(userId);
        assertEquals(ExternalLoginCallbackDTO.Outcome.BOUND, callback().getOutcome());
        assertEquals(userId, accounts.account.getUserId());
        assertEquals(0, issuedSessions);
    }

    @Test
    void rejectsInvalidUserIdsBeforeAnyWrite() {
        for (String value : List.of("-1", "0", "1.0", "invalid", "9223372036854775808")) {
            authorize(null);
            identity = identity(value);
            assertThrows(BizException.class, this::callback);
        }
        assertNull(accounts.account);
        assertEquals(0, issuedSessions);
    }

    @Test
    void disabledProviderAndMismatchedStateNeverAutoBind() {
        enabled = false;
        assertThrows(BizException.class, this::callback);
        assertEquals(0, exchanges);
        enabled = true;
        authorize(null);
        assertThrows(BizException.class, () -> app.callbackByState("proof", "state", "cas", "eduroam"));
        assertEquals(0, exchanges);
        assertEquals(0, issuedSessions);
    }

    private PluginExternalLoginIdentity identity(String localId) {
        return new PluginExternalLoginIdentity("student@school.edu.cn", "student", null, null, null, localId);
    }
    private void authorize(Long bindId) {
        tickets.saveState("state", new ExternalLoginTicketStore.State("eduroam", "eduroam", bindId));
    }
    private ExternalLoginCallbackDTO callback() {
        return app.callbackByState("proof", "state", "eduroam", "eduroam");
    }
    private void bindTo(Long id) {
        accounts.account = ExternalAccount.builder().userId(id).providerCode("eduroam")
                .platformType("eduroam").socialUid("student@school.edu.cn").build();
    }
    private static class Accounts implements ExternalAccountRepo {
        ExternalAccount account;
        public ExternalAccount save(ExternalAccount value) { account = value; return value; }
        public Optional<ExternalAccount> findByProviderAndPlatformAndSocialUid(String p, String t, String s) {
            return Optional.ofNullable(account);
        }
        public List<ExternalAccount> findByUserId(Long id) { return List.of(); }
        public Optional<ExternalAccount> findByIdAndUserId(Long id, Long uid) { return Optional.empty(); }
        public void deleteById(Long id) { throw new AssertionError("unexpected delete"); }
    }
    private static class Tickets implements ExternalLoginTicketStore {
        final Map<String, State> states = new HashMap<>();
        final Map<String, Binding> bindings = new HashMap<>();
        public void saveState(String token, State value) { states.put(token, value); }
        public Optional<State> consumeState(String token) { return Optional.ofNullable(states.remove(token)); }
        public void saveBinding(String token, Binding value) { bindings.put(token, value); }
        public Optional<Binding> consumeBinding(String token) { return Optional.ofNullable(bindings.remove(token)); }
    }
}
