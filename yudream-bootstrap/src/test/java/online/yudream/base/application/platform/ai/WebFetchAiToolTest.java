package online.yudream.base.application.platform.ai;

import online.yudream.base.application.platform.ai.service.WebFetchAiTool;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebFetchAiToolTest {

    @Test
    void rejectsNonSuccessHttpResponse() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("forbidden");
        when(response.headers()).thenReturn(HttpHeadersStub.of("text/html; charset=utf-8"));
        doReturn(response).when(client).send(any(), any());
        var tool = new WebFetchAiTool(client);

        assertThatThrownBy(() -> tool.execute(new AiAgentToolCall(
                WebFetchAiTool.TOOL_NAME,
                Map.of("url", "https://example.com/private")
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HTTP 403");
    }

    @Test
    void returnsPageSummaryForSuccessResponse() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("<html><head><title>参考站点</title><meta name=\"description\" content=\"页面摘要\"></head><body><h1>页面正文</h1></body></html>");
        when(response.headers()).thenReturn(HttpHeadersStub.of("text/html; charset=utf-8"));
        doReturn(response).when(client).send(any(), any());
        var tool = new WebFetchAiTool(client);

        var result = tool.execute(new AiAgentToolCall(
                WebFetchAiTool.TOOL_NAME,
                Map.of("url", "https://example.com")
        ));

        assertThat(result.message()).contains("已抓取");
        assertThat(result.payload())
                .containsEntry("status", 200)
                .containsEntry("title", "参考站点")
                .containsEntry("description", "页面摘要");
        assertThat(String.valueOf(result.payload().get("content"))).contains("页面正文");
    }

    private static final class HttpHeadersStub {

        private HttpHeadersStub() {
        }

        private static java.net.http.HttpHeaders of(String contentType) {
            return java.net.http.HttpHeaders.of(
                    Map.of("Content-Type", java.util.List.of(contentType)),
                    (name, value) -> true
            );
        }
    }
}
