package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.user.cmd.UserLoginCmd;
import online.yudream.base.application.system.user.cmd.UserRegisterCmd;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.plugin.spi.system.auth.ExtensionVeto;
import online.yudream.base.plugin.spi.system.auth.IdentityVerificationMethod;
import online.yudream.base.plugin.spi.system.auth.IdentityVerificationProvider;
import online.yudream.base.plugin.spi.system.auth.IdentityVerificationResult;
import online.yudream.base.plugin.spi.system.auth.LoginInterceptor;
import online.yudream.base.plugin.spi.system.auth.RegisterInterceptor;
import online.yudream.base.plugin.spi.system.auth.VerificationSubject;
import online.yudream.base.plugin.spi.system.extension.PluginExtensionQuery;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 登录/注册扩展门禁的定向测试：只覆盖拦截器否决、fail-closed、
 * 必需身份核验与可用方式清单，门禁失败的用例不会触碰仓储，依赖以 null 传入。
 */
class UserAppServiceExtensionGateTest {

    @Test
    void registerDeniedByInterceptorReturnsReason() {
        RegisterInterceptor interceptor = attempt -> ExtensionVeto.deny("该邮箱域名不允许注册");
        UserAppService service = service(null, Map.of(RegisterInterceptor.class, List.of(interceptor)));

        BizException exception = assertThrows(BizException.class,
                () -> service.register(registerCmd()));

        assertEquals("该邮箱域名不允许注册", exception.getMessage());
    }

    @Test
    void registerInterceptorExceptionFailsClosed() {
        RegisterInterceptor broken = attempt -> {
            throw new IllegalStateException("boom");
        };
        UserAppService service = service(null, Map.of(RegisterInterceptor.class, List.of(broken)));

        BizException exception = assertThrows(BizException.class,
                () -> service.register(registerCmd()));

        assertEquals("注册校验服务异常，请稍后再试", exception.getMessage());
    }

    @Test
    void registerRejectsMissingRequiredVerificationProvider() {
        SettingRepo settings = settingsWithRequiredVerifications("xuexin");
        UserAppService service = service(settings, Map.of());

        BizException exception = assertThrows(BizException.class,
                () -> service.register(registerCmd()));

        assertEquals("身份核验方式不可用：xuexin，请联系管理员", exception.getMessage());
    }

    @Test
    void registerRejectsUnverifiedSubjectWithProviderMessage() {
        IdentityVerificationProvider provider = provider("xuexin", subject -> IdentityVerificationResult.unverified("请先完成学信网身份核验"));
        SettingRepo settings = settingsWithRequiredVerifications("xuexin");
        UserAppService service = service(settings, Map.of(IdentityVerificationProvider.class, List.of(provider)));

        BizException exception = assertThrows(BizException.class,
                () -> service.register(registerCmd()));

        assertEquals("请先完成学信网身份核验", exception.getMessage());
    }

    @Test
    void loginDeniedByInterceptorReturnsReason() {
        LoginInterceptor interceptor = attempt -> ExtensionVeto.deny("账号来源受限");
        UserAppService service = service(null, Map.of(LoginInterceptor.class, List.of(interceptor)));

        BizException exception = assertThrows(BizException.class,
                () -> service.login(UserLoginCmd.builder().username("demo").password("secret").build()));

        assertEquals("账号来源受限", exception.getMessage());
    }

    @Test
    void loginInterceptorExceptionFailsClosed() {
        LoginInterceptor broken = attempt -> {
            throw new IllegalStateException("boom");
        };
        UserAppService service = service(null, Map.of(LoginInterceptor.class, List.of(broken)));

        BizException exception = assertThrows(BizException.class,
                () -> service.login(UserLoginCmd.builder().username("demo").password("secret").build()));

        assertEquals("登录校验服务异常，请稍后再试", exception.getMessage());
    }

    @Test
    void availableVerificationMethodsSortedAndSkipsBrokenProviders() {
        IdentityVerificationProvider second = provider("b", null, 20);
        IdentityVerificationProvider broken = new IdentityVerificationProvider() {
            @Override
            public IdentityVerificationMethod method() {
                throw new IllegalStateException("boom");
            }

            @Override
            public IdentityVerificationResult check(VerificationSubject subject) {
                return IdentityVerificationResult.passed();
            }
        };
        IdentityVerificationProvider first = provider("a", null, 10);
        UserAppService service = service(null,
                Map.of(IdentityVerificationProvider.class, List.of(second, broken, first)));

        List<IdentityVerificationMethod> methods = service.availableVerificationMethods();

        assertEquals(List.of("a", "b"), methods.stream().map(IdentityVerificationMethod::code).toList());
    }

    private IdentityVerificationProvider provider(String code, CheckBehavior behavior) {
        return provider(code, behavior, 0);
    }

    private IdentityVerificationProvider provider(String code, CheckBehavior behavior, int sort) {
        return new IdentityVerificationProvider() {
            @Override
            public IdentityVerificationMethod method() {
                return new IdentityVerificationMethod(code, code + "-name", null, null, sort);
            }

            @Override
            public IdentityVerificationResult check(VerificationSubject subject) {
                return behavior == null ? IdentityVerificationResult.passed() : behavior.check(subject);
            }
        };
    }

    private UserAppService service(SettingRepo settingRepo, Map<Class<?>, List<?>> extensions) {
        PluginExtensionQuery query = new PluginExtensionQuery() {
            @Override
            @SuppressWarnings("unchecked")
            public <I> List<I> extensions(Class<I> extensionPoint) {
                return (List<I>) extensions.getOrDefault(extensionPoint, List.of());
            }
        };
        SettingRepo settings = settingRepo == null ? emptySettings() : settingRepo;
        return new UserAppService(null, settings, null, null, query, null, null, null, null, null, null, null, null, null);
    }

    private SettingRepo settingsWithRequiredVerifications(String codes) {
        return new StubSettingRepo(Optional.of(Setting.builder()
                .key(UserAppService.REQUIRED_VERIFICATIONS_SETTING_KEY)
                .value(codes)
                .build()));
    }

    private SettingRepo emptySettings() {
        return new StubSettingRepo(Optional.empty());
    }

    private UserRegisterCmd registerCmd() {
        return UserRegisterCmd.builder()
                .username("demo")
                .nickname("Demo")
                .email("demo@example.com")
                .password("secret")
                .build();
    }

    private interface CheckBehavior {
        IdentityVerificationResult check(VerificationSubject subject);
    }

    private static class StubSettingRepo implements SettingRepo {
        private final Map<String, Setting> byKey = new HashMap<>();

        StubSettingRepo(Optional<Setting> setting) {
            setting.ifPresent(value -> byKey.put(value.getKey(), value));
        }

        @Override
        public Setting save(Setting setting) {
            byKey.put(setting.getKey(), setting);
            return setting;
        }

        @Override
        public Optional<Setting> findByKey(String key) {
            return Optional.ofNullable(byKey.get(key));
        }

        @Override
        public boolean existsByKey(String key) {
            return byKey.containsKey(key);
        }

        @Override
        public List<Setting> findByCategory(String category) {
            return List.of();
        }

        @Override
        public List<Setting> findAll() {
            return List.copyOf(byKey.values());
        }
    }
}
