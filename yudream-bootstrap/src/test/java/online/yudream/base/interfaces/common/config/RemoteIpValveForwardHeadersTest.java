package online.yudream.base.interfaces.common.config;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.tomcat.util.http.MimeHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证启用 {@code server.forward-headers-strategy=native} 后，容器内置 RemoteIpValve 只信任内部代理，
 * 用可信代理注入的 X-Forwarded-For 重写 {@code getRemoteAddr()}，从而让限流按真实客户端 IP 隔离。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RemoteIpValveForwardHeadersTest {

    @Mock
    private Connector connector;

    @Test
    void trustedInternalProxyResolvesForwardedForToClientIp() throws Exception {
        Request request = proxyRequest("172.18.0.5");
        when(request.getHeaders("X-Forwarded-For")).thenReturn(Collections.enumeration(List.of("203.0.113.7")));
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7");

        valve().invoke(request, mock(Response.class));

        verify(request).setRemoteAddr("203.0.113.7");
    }

    @Test
    void untrustedDirectClientForwardedForIsIgnored() throws Exception {
        // remoteAddr 不是内部代理地址，说明请求未经可信反代，客户端伪造的 XFF 必须被忽略。
        Request request = proxyRequest("203.0.113.9");
        when(request.getHeaders("X-Forwarded-For")).thenReturn(Collections.enumeration(List.of("198.51.100.1")));
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1");

        valve().invoke(request, mock(Response.class));

        verify(request, never()).setRemoteAddr("198.51.100.1");
    }

    private RemoteIpValve valve() {
        RemoteIpValve valve = new RemoteIpValve();
        valve.setNext(mock(Valve.class));
        return valve;
    }

    private Request proxyRequest(String remoteAddr) {
        Request request = mock(Request.class);
        org.apache.coyote.Request coyote = mock(org.apache.coyote.Request.class);

        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getRemoteHost()).thenReturn(remoteAddr);
        when(request.getScheme()).thenReturn("http");
        when(request.isSecure()).thenReturn(false);
        when(request.getServerName()).thenReturn("backend");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getLocalPort()).thenReturn(8080);
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        // 模拟异步阶段，跳过 RemoteIpValve 在请求结束后的远端地址恢复，聚焦校验其解析结果。
        when(request.isAsync()).thenReturn(true);
        when(request.getConnector()).thenReturn(connector);
        when(connector.getEnableLookups()).thenReturn(false);
        when(request.getCoyoteRequest()).thenReturn(coyote);
        when(coyote.getMimeHeaders()).thenReturn(new MimeHeaders());
        return request;
    }
}
