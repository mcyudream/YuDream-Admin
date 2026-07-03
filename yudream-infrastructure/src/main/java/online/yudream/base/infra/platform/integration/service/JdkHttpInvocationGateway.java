package online.yudream.base.infra.platform.integration.service;

import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;
import online.yudream.base.domain.platform.integration.service.HttpInvocationGateway;
import online.yudream.base.domain.platform.integration.valobj.HttpInvocationResult;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JdkHttpInvocationGateway implements HttpInvocationGateway {

    @Override
    public HttpInvocationResult invoke(HttpConnector connector, Map<String, String> headers, Map<String, String> queryParams, String body) {
        long start = System.currentTimeMillis();
        int attempts = Math.max(connector.getRetryTimes(), 0) + 1;
        Exception lastError = null;
        for (int i = 0; i < attempts; i++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(urlWithQuery(connector.getUrl(), queryParams)))
                        .timeout(Duration.ofMillis(connector.getTimeoutMillis()));
                headers.forEach(builder::header);
                builder.method(connector.getMethod().name(), bodyPublisher(connector.getMethod(), body));
                HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
                return new HttpInvocationResult(response.statusCode(), response.body(), elapsed(start), ExecutionStatus.SUCCESS, null);
            } catch (Exception e) {
                lastError = e;
            }
        }
        return new HttpInvocationResult(0, null, elapsed(start), ExecutionStatus.FAILED, lastError == null ? "HTTP 调用失败" : lastError.getMessage());
    }

    private HttpRequest.BodyPublisher bodyPublisher(HttpMethodType method, String body) {
        if (method == HttpMethodType.GET || method == HttpMethodType.DELETE) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8);
    }

    private String urlWithQuery(String url, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        String query = queryParams.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
