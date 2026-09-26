package online.yudream.base.interfaces.platform.plugin.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 握手 Origin 策略：同源放行、白名单放行、缺失仅 header 凭据放行、
 * 不信任 X-Forwarded-*（仅按直连请求 host 比对）。
 */
class PluginWsOriginPolicyTest {

    private final PluginWsOriginPolicy policy = new PluginWsOriginPolicy(List.of("https://demo.example.com"));

    private static final URI LOCAL = URI.create("http://127.0.0.1:8080/");

    @Test
    @DisplayName("同源（含隐式端口）放行")
    void sameOriginAllowed() {
        assertTrue(policy.allows("http://127.0.0.1:8080", LOCAL, false));
        assertTrue(policy.allows("http://127.0.0.1:8080", LOCAL, true));
    }

    @Test
    @DisplayName("跨域仅显式白名单放行")
    void crossOriginNeedsAllowList() {
        assertFalse(policy.allows("http://evil.example.com", LOCAL, true));
        assertTrue(policy.allows("https://demo.example.com", LOCAL, true));
    }

    @Test
    @DisplayName("Origin 缺失仅 Authorization 凭据通道放行")
    void missingOriginOnlyForHeaderCredential() {
        assertFalse(policy.allows(null, LOCAL, false));
        assertFalse(policy.allows(" ", LOCAL, false));
        assertTrue(policy.allows(null, LOCAL, true));
    }

    @Test
    @DisplayName("畸形 Origin 一律拒绝")
    void malformedOriginRejected() {
        assertFalse(policy.allows("not-a-origin", LOCAL, true));
    }

    @Test
    @DisplayName("直连 host 才参与比对：伪造 Forwarded 头不影响结果")
    void forwardedHeadersNotTrusted() {
        // Origin 是另一个站点、白名单为空时：无论请求是否经过代理，策略都拒绝
        PluginWsOriginPolicy strict = new PluginWsOriginPolicy(List.of());
        assertFalse(strict.allows("https://public.example.com", LOCAL, true));
    }

    @Test
    @DisplayName("host 大小写归一后比对")
    void hostCaseInsensitive() {
        URI localhost = URI.create("http://localhost:8080/");
        assertTrue(policy.allows("http://LOCALHOST:8080", localhost, true));
        assertTrue(policy.allows("http://localhost:8080", URI.create("http://LocalHost:8080/"), true));
    }
}
