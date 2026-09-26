package online.yudream.base.interfaces.common.interceptor;

import online.yudream.base.application.system.monitor.service.SystemMonitorAppService;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.interfaces.common.RequestFailureContext;
import online.yudream.base.interfaces.common.config.WebLogProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebInvokeTimeInterceptorTest {

    @Test
    void recordsHandledFailureAndMasksSensitiveQuery() {
        SystemMonitorAppService monitorService = mock(SystemMonitorAppService.class);
        WebInvokeTimeInterceptor interceptor = new WebInvokeTimeInterceptor(properties(), monitorService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        request.setQueryString("token=abc&name=test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestFailureContext.mark(request, new IllegalArgumentException());

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<ApiLogDTO> captor = ArgumentCaptor.forClass(ApiLogDTO.class);
        verify(monitorService).recordApiLog(captor.capture());
        ApiLogDTO apiLog = captor.getValue();
        assertThat(apiLog.getSuccess()).isFalse();
        assertThat(apiLog.getErrorMessage()).isEqualTo("IllegalArgumentException");
        assertThat(apiLog.getQuery()).isEqualTo("token=******&name=test");
    }

    @Test
    void suppressesAuditPersistenceFailure() {
        SystemMonitorAppService monitorService = mock(SystemMonitorAppService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(monitorService).recordApiLog(any(ApiLogDTO.class));
        WebInvokeTimeInterceptor interceptor = new WebInvokeTimeInterceptor(properties(), monitorService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThatCode(() -> interceptor.afterCompletion(request, response, new Object(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void masksEnrollTokenInJsonBodyAndKeepsOtherAuditContent() {
        ApiLogDTO apiLog = record("POST", "/api/plugins/mcpanel/node/bootstrap", null,
                "{\"enrollToken\":\"tok_enroll_123\",\"hostname\":\"node-1\",\"caps\":[\"basic\"]}");
        assertThat(apiLog.getRequestBody())
                .contains("\"enrollToken\":\"******\"")
                .contains("\"hostname\":\"node-1\"")
                .doesNotContain("tok_enroll_123");
    }

    @Test
    void masksCredentialKeysWithPrefixSuffixSeparatorAndCase() {
        ApiLogDTO apiLog = record("POST", "/api/example", null,
                "{\"nodeSecret\":\"s1\",\"access_token\":\"a1\",\"refreshToken\":\"r1\",\"apiKey\":\"k1\","
                        + "\"X-API-KEY\":\"k2\",\"private_key\":\"pk\",\"PASSWORD\":\"p1\","
                        + "\"Authorization\":\"Bearer b1\",\"cookie\":\"c1\",\"WS_TICKET\":\"t1\"}");
        String body = apiLog.getRequestBody();
        assertThat(body).doesNotContain("s1", "a1", "r1", "k1", "k2", "pk", "p1", "b1", "c1", "t1");
        assertThat(body)
                .contains("\"nodeSecret\":\"******\"")
                .contains("\"access_token\":\"******\"")
                .contains("\"refreshToken\":\"******\"")
                .contains("\"apiKey\":\"******\"")
                .contains("\"X-API-KEY\":\"******\"")
                .contains("\"private_key\":\"******\"")
                .contains("\"PASSWORD\":\"******\"")
                .contains("\"Authorization\":\"******\"")
                .contains("\"cookie\":\"******\"")
                .contains("\"WS_TICKET\":\"******\"");
    }

    @Test
    void masksUnicodeEscapedJsonKeysByDecodedName() {
        ApiLogDTO apiLog = record("POST", "/api/plugins/mcpanel/node/bootstrap", null,
                "{\"enroll\\u0054oken\":\"fake-value\"}");
        assertThat(apiLog.getRequestBody())
                .contains("\"enrollToken\":\"******\"")
                .doesNotContain("fake-value");
    }

    @Test
    void masksJsonStringValuesAcrossEscapedQuotes() {
        ApiLogDTO apiLog = record("POST", "/api/example", null,
                "{\"password\":\"a\\\"b\",\"remark\":\"保留 \\\"引号\\\" 原文\"}");
        String body = apiLog.getRequestBody();
        assertThat(body)
                .contains("\"password\":\"******\"")
                .contains("保留 \\\"引号\\\" 原文")
                .doesNotContain("a\\\"b");
    }

    @Test
    void masksWholeValueOfSensitiveKeysOnArraysAndObjects() {
        ApiLogDTO apiLog = record("POST", "/api/example", null,
                "{\"tokens\":[\"a1\",\"b2\"],\"credentials\":{\"login\":\"u1\",\"pwdHash\":\"h1\"},"
                        + "\"tags\":[\"ops\"],\"remark\":\"ok\"}");
        String body = apiLog.getRequestBody();
        assertThat(body)
                .contains("\"tokens\":\"******\"")
                .contains("\"credentials\":\"******\"")
                .doesNotContain("a1", "b2", "u1", "h1")
                .contains("\"tags\":[\"ops\"]")
                .contains("\"remark\":\"ok\"");
    }

    @Test
    void masksNestedJsonAndArrayOfObjects() {
        ApiLogDTO apiLog = record("POST", "/api/example", null,
                "{\"outer\":{\"password\":\"p1\"},\"list\":[{\"token\":\"t1\"},{\"token\":\"t2\"}]}");
        String body = apiLog.getRequestBody();
        assertThat(body)
                .contains("\"password\":\"******\"")
                .doesNotContain("p1", "t1", "t2");
        // 两个 "token" 键值对全部打码，且无其他 token 字样
        assertThat(body.split("token", -1).length - 1).isEqualTo(2);
    }

    @Test
    void masksArbitraryDepthCredentialObjectsFailClosed() {
        // 凭据值使用足够唯一的虚构串，避免单字符断言误伤键名（如 secretKey 的结尾 y）
        ApiLogDTO apiLog = record("POST", "/api/example", null,
                "{\"a\":{\"b\":{\"credentials\":{\"x\":\"fake-cred-child-01\","
                        + "\"more\":{\"innerToken\":\"fake-inner-02\"}}}},"
                        + "\"l1\":{\"l2\":{\"l3\":{\"l4\":{\"secretKey\":\"fake-deep-03\"}}}}}");
        // 精确断言整棵掩码后的树：凭据对象/深层键均为整值 ******
        assertThat(apiLog.getRequestBody()).isEqualTo(
                "{\"a\":{\"b\":{\"credentials\":\"******\"}},"
                        + "\"l1\":{\"l2\":{\"l3\":{\"l4\":{\"secretKey\":\"******\"}}}}}");
    }

    @Test
    void keepsNonSensitiveFieldValuesVerbatim() {
        ApiLogDTO apiLog = record("POST", "/api/example", null,
                "{\"callback\":\"https://x?token=abc123\",\"secret\":\"s9\","
                        + "\"data\":\"{\\\"token\\\":\\\"inner1\\\"}\"}");
        String body = apiLog.getRequestBody();
        // 非敏感键的值原样保留（内嵌 URL token/逃逸 JSON 不再深挖）；敏感键整值打码
        assertThat(body)
                .contains("https://x?token=abc123")
                .contains("\\\"token\\\":\\\"inner1\\\"")
                .contains("\"secret\":\"******\"")
                .doesNotContain("\"secret\":\"s9\"");
    }

    @Test
    void keepsExistingContentKeyMaskingWithoutFragmentOverreach() {
        ApiLogDTO apiLog = record("POST", "/api/example", null,
                "{\"message\":\"hello\",\"content\":\"c\",\"html\":\"<p>\",\"prompt\":\"p\","
                        + "\"messageId\":\"m-1\",\"contentType\":\"text/plain\",\"context\":\"ctx\"}");
        String body = apiLog.getRequestBody();
        assertThat(body)
                .contains("\"message\":\"******\"")
                .contains("\"content\":\"******\"")
                .contains("\"html\":\"******\"")
                .contains("\"prompt\":\"******\"")
                .contains("\"messageId\":\"m-1\"")
                .contains("\"contentType\":\"text/plain\"")
                .contains("\"context\":\"ctx\"");
    }

    @Test
    void masksWsTicketAndUrlEncodedValuesInQuery() {
        ApiLogDTO apiLog = record("GET", "/api/platform/plugins/ws/mcpanel/ctl",
                "ws_ticket=tok%20en123&name=x", null);
        assertThat(apiLog.getQuery()).isEqualTo("ws_ticket=******&name=x");
    }

    @Test
    void masksUrlEncodedCredentialKeysAndValuesInQuery() {
        ApiLogDTO apiLog = record("GET", "/api/example", "access_token=abc.def&state=y", null);
        assertThat(apiLog.getQuery()).isEqualTo("access_token=******&state=y");
        // 键含 URL 编码（%5F = _）：先解码匹配 ws_ticket，输出保留原始编码形态
        ApiLogDTO encodedKey = record("GET", "/api/platform/plugins/ws/mcpanel/ctl",
                "ws%5Fticket=zz9&ok=1", null);
        assertThat(encodedKey.getQuery()).isEqualTo("ws%5Fticket=******&ok=1");
        ApiLogDTO encodedToken = record("GET", "/api/example", "access%5Ftoken=vv1&ok=1", null);
        assertThat(encodedToken.getQuery()).isEqualTo("access%5Ftoken=******&ok=1");
    }

    @Test
    void failsClosedOnIllegalJson() {
        String placeholder = "[请求体无法安全解析，已省略]";
        // 截断体：整段占位符，不保留猜测原文
        ApiLogDTO truncated = record("POST", "/api/plugins/mcpanel/node/bootstrap", null,
                "{\"enrollToken\":\"tok_start_1");
        assertThat(truncated.getRequestBody()).isEqualTo(placeholder);
        // 截断的 Unicode 逃逸键体：同样整段占位符
        ApiLogDTO escaped = record("POST", "/api/plugins/mcpanel/node/bootstrap", null,
                "{\"enroll\\u0054oken\":\"known-token");
        assertThat(escaped.getRequestBody()).isEqualTo(placeholder);
        // 多层凭据结构截断：整段占位符
        ApiLogDTO deep = record("POST", "/api/example", null,
                "{\"credentials\":{\"a\":[1,2");
        assertThat(deep.getRequestBody()).isEqualTo(placeholder);
        // 尾随垃圾：trailing token 视为解析失败，整段占位符
        ApiLogDTO trailing = record("POST", "/api/example", null,
                "{\"secret\":\"a1\"}trailing");
        assertThat(trailing.getRequestBody()).isEqualTo(placeholder);
        // 非法 UTF-8 字节（容器层解码为替换符）不破坏解析，仍按键名整值打码
        String invalidUtf = new String(new byte[]{'{', '"', 'e', 'n', 'r', 'o', 'l', 'l', 'T', 'o', 'k',
                'e', 'n', '"', ':', '"', (byte) 0xFF, '"', '}'}, StandardCharsets.UTF_8);
        ApiLogDTO invalidUtfBody = record("POST", "/api/plugins/mcpanel/node/bootstrap", null, invalidUtf);
        assertThat(invalidUtfBody.getRequestBody()).contains("\"enrollToken\":\"******\"");
    }

    @Test
    void leavesBodiesWithoutSensitiveFieldsUntouched() {
        String raw = "{\"hostname\":\"node-1\",\"caps\":[\"basic\"],\"remark\":\"timeout=30 not masked\"}";
        ApiLogDTO apiLog = record("POST", "/api/plugins/mcpanel/node/bootstrap", null, raw);
        // 未命中敏感键原样返回（字节不变，不重序列化）
        assertThat(apiLog.getRequestBody()).isEqualTo(raw);
    }

    private ApiLogDTO record(String method, String uri, String queryString, String body) {
        SystemMonitorAppService monitorService = mock(SystemMonitorAppService.class);
        WebInvokeTimeInterceptor interceptor = new WebInvokeTimeInterceptor(properties(), monitorService);
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        // 对齐生产 Boot 默认 CharacterEncodingFilter（请求未声明 charset 时按 UTF-8）；
        // Mock 默认 ISO-8859-1 会让 formatBody 中文乱码
        request.setCharacterEncoding("UTF-8");
        if (queryString != null) {
            request.setQueryString(queryString);
        }
        HttpServletRequest actual = request;
        if (body != null) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            // 6.2.18 起要求显式缓存上限；测试 body 远小于 64KB
            ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request, 64 * 1024);
            try {
                wrapper.getInputStream().readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            actual = wrapper;
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(actual, response, new Object());
        interceptor.afterCompletion(actual, response, new Object(), null);
        ArgumentCaptor<ApiLogDTO> captor = ArgumentCaptor.forClass(ApiLogDTO.class);
        verify(monitorService).recordApiLog(captor.capture());
        return captor.getValue();
    }

    private WebLogProperties properties() {
        WebLogProperties properties = new WebLogProperties();
        properties.setEnabled(true);
        return properties;
    }
}
