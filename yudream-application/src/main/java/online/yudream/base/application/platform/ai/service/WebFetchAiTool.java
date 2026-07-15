package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class WebFetchAiTool implements AiAgentTool {

    public static final String TOOL_NAME = "web.fetch";
    public static final String PERMISSION_CODE = "platform:ai:tool:web-fetch";

    private static final int MAX_BODY_LENGTH = 6_000;
    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern DESCRIPTION = Pattern.compile("(?is)<meta\\s+[^>]*(name|property)=[\"'](?:description|og:description)[\"'][^>]*content=[\"'](.*?)[\"'][^>]*>");

    private final HttpClient httpClient;

    public WebFetchAiTool() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public WebFetchAiTool(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public AiAgentToolDescriptor descriptor() {
        return new AiAgentToolDescriptor(
                TOOL_NAME,
                "网页抓取",
                "抓取公开网页的标题、描述和正文摘要，供 AI 在修改 CMS 页面前分析参考。",
                PERMISSION_CODE,
                "AI 抓取网页",
                "平台能力",
                "允许 AI Agent 抓取公开网页作为页面构建参考。",
                Map.of(
                        "url", Map.of("type", "string", "description", "需要抓取的 http/https 页面地址"),
                        "purpose", Map.of("type", "string", "description", "抓取该页面的分析目的")
                )
        );
    }

    @Override
    public AiAgentToolResult execute(AiAgentToolCall call) {
        Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
        String url = text(args.get("url"));
        if (!StringUtils.hasText(url)) {
            throw new BizException("web.fetch 缺少 url 参数");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BizException("web.fetch 地址无效：" + url);
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BizException("web.fetch 仅支持 http/https 地址");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 YuDream-AI-WebFetch/1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/json,text/plain;q=0.9,*/*;q=0.2")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("web.fetch 请求失败：HTTP " + response.statusCode() + responseDetail(response.body()));
            }
            if (!isTextContent(contentType)) {
                throw new BizException("web.fetch 响应不是可分析的文本内容：" + contentType);
            }
            if (!StringUtils.hasText(response.body())) {
                throw new BizException("web.fetch 响应正文为空");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("url", uri.toString());
            payload.put("status", response.statusCode());
            payload.put("contentType", contentType);
            payload.put("title", extract(TITLE, response.body()));
            payload.put("description", extract(DESCRIPTION, response.body()));
            payload.put("content", normalize(response.body()));
            return new AiAgentToolResult(
                    TOOL_NAME,
                    "fetch",
                    PERMISSION_CODE,
                    "网页参考内容已抓取。",
                    payload
            );
        } catch (IOException e) {
            throw new BizException("web.fetch 抓取失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("web.fetch 抓取被中断");
        }
    }

    private String extract(Pattern pattern, String html) {
        var matcher = pattern.matcher(html == null ? "" : html);
        return matcher.find() ? cleanText(matcher.group(matcher.groupCount())) : "";
    }

    private String normalize(String html) {
        String text = SCRIPT_STYLE.matcher(html == null ? "" : html).replaceAll(" ");
        text = TAG.matcher(text).replaceAll(" ");
        text = cleanText(text);
        return text.length() > MAX_BODY_LENGTH ? text.substring(0, MAX_BODY_LENGTH) : text;
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private boolean isTextContent(String contentType) {
        String value = contentType == null ? "" : contentType.toLowerCase();
        return !StringUtils.hasText(value)
                || value.startsWith("text/")
                || value.contains("json")
                || value.contains("xml")
                || value.contains("javascript");
    }

    private String responseDetail(String body) {
        String detail = cleanText(normalize(body));
        return StringUtils.hasText(detail) ? "：" + detail.substring(0, Math.min(detail.length(), 180)) : "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
