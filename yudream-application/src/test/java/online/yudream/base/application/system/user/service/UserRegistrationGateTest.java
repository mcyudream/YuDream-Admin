package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.user.cmd.UserRegisterCmd;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开放注册硬门禁：关闭后 /register 在插件拦截器之前即被拒绝；
 * 开关缺省（无策略记录或新建默认策略）时保持开放，行为向后兼容。
 */
class UserRegistrationGateTest {

    @Test
    void closedRegistrationRejectsBeforeAnythingElse() {
        ApiSecurityPolicy closed = ApiSecurityPolicy.createDefault();
        closed.updateSwitches(false, false, false, false, false, false, false);
        ApiSecurityPolicyRepo repo = stubRepo(Optional.of(closed));
        UserAppService service = new UserAppService(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, repo);

        UserRegisterCmd cmd = new UserRegisterCmd();
        cmd.setUsername("newuser");
        BizException rejected = assertThrows(BizException.class, () -> service.register(cmd));
        assertEquals("本站已关闭开放注册，请联系管理员开通账号", rejected.getMessage());
    }

    @Test
    void missingPolicyDefaultsToOpenRegistration() {
        // 无策略记录时按默认（开放）放行：注册会继续走到后续依赖（此处为 null），
        // 因此以「非注册门禁 BizException」证明已通过门禁。
        ApiSecurityPolicyRepo repo = stubRepo(Optional.empty());
        UserAppService service = new UserAppService(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, repo);

        UserRegisterCmd cmd = new UserRegisterCmd();
        cmd.setUsername("newuser");
        Exception thrown = assertThrows(Exception.class, () -> service.register(cmd));
        assertTrue(!(thrown instanceof BizException gateError)
                || !"本站已关闭开放注册，请联系管理员开通账号".equals(gateError.getMessage()),
                "缺省策略不应触发注册门禁，实际: " + thrown);
    }

    @Test
    void defaultPolicyKeepsRegistrationOpen() {
        assertTrue(ApiSecurityPolicy.createDefault().isRegistrationEnabled());
        ApiSecurityPolicy policy = ApiSecurityPolicy.createDefault();
        policy.updateSwitches(false, false, false, false, false, false, true);
        assertTrue(policy.isRegistrationEnabled());
    }

    private static ApiSecurityPolicyRepo stubRepo(Optional<ApiSecurityPolicy> policy) {
        return (ApiSecurityPolicyRepo) Proxy.newProxyInstance(
                ApiSecurityPolicyRepo.class.getClassLoader(),
                new Class<?>[] { ApiSecurityPolicyRepo.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("findDefault")) {
                        return policy;
                    }
                    if (method.getName().equals("save")) {
                        return args[0];
                    }
                    throw new AssertionError(method.getName());
                });
    }
}
