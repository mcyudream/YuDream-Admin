package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.service.WikiWebSearchGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebSearchConfig;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebSearchResult;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tavily / SerpApi / SearXNG 网络搜索实现。
 * <p>
 * Tavily 通过 include_raw_content 返回完整正文；SerpApi/SearXNG 在拿到链接后抓取正文并清洗为纯文本。
 */
@Service
public class HttpWikiWebSearchGateway implements WikiWebSearchGateway {

    private static final int DEFAULT_LIMIT = 5;
    private static final int PAGE_CONTENT_LIMIT = 12_000;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public List<WikiWebSearchResult> search(WikiWebSearchConfig config, String query, int limit) {
        if (config == null || !config.enabled()) {
            throw new BizException("网络搜索 Provider 未配置");
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int resultLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 20);
        return switch (config.providerCode().trim().toLowerCase(Locale.ROOT)) {
            case "tavily" -> searchTavily(config, query, resultLimit);
            case "serpapi" -> searchSerpApi(config, query, resultLimit);
            case "searxng" -> searchSearxng(config, query, resultLimit);
            default -> throw new BizException("不支持的搜索 Provider：" + config.providerCode());
        };
    }

    @SuppressWarnings("unchecked")
    private List<WikiWebSearchResult> searchTavily(WikiWebSearchConfig config, String query, int limit) {
        if (!StringUtils.hasText(config.apiKey())) {
            throw new BizException("Tavily API Key 未配置");
        }
        Map<String, Object> response = RestClient.create().post()
                .uri("https://api.tavily.com/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "api_key", config.apiKey(),
                        "query", query,
                        "search_depth", "advanced",
                        "max_results", limit,
                        "include_raw_content", true
                ))
                .retrieve().body(Map.class);
        List<WikiWebSearchResult> results = new ArrayList<>();
        Object data = response == null ? null : response.get("results");
        if (data instanceof List<?> rows) {
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> map)) {
                    continue;
                }
                String raw = stringValue(map.get("raw_content"));
                String content = StringUtils.hasText(raw) ? raw : stringValue(map.get("content"));
                results.add(new WikiWebSearchResult(
                        stringValue(map.get("title")),
                        stringValue(map.get("url")),
                        stringValue(map.get("content")),
                        content
                ));
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private List<WikiWebSearchResult> searchSerpApi(WikiWebSearchConfig config, String query, int limit) {
        if (!StringUtils.hasText(config.apiKey())) {
            throw new BizException("SerpApi API Key 未配置");
        }
        String engine = StringUtils.hasText(config.engine()) ? config.engine() : "google";
        Map<String, Object> response = RestClient.create().get()
                .uri(uri -> uri.scheme("https").host("serpapi.com").path("/search.json")
                        .queryParam("engine", engine)
                        .queryParam("q", query)
                        .queryParam("api_key", config.apiKey())
                        .queryParam("num", limit)
                        .build())
                .retrieve().body(Map.class);
        List<WikiWebSearchResult> results = new ArrayList<>();
        Object data = response == null ? null : response.get("organic_results");
        if (data instanceof List<?> rows) {
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> map)) {
                    continue;
                }
                String url = stringValue(map.get("link"));
                String snippet = stringValue(map.get("snippet"));
                results.add(new WikiWebSearchResult(
                        stringValue(map.get("title")),
                        url,
                        snippet,
                        fetchPageContent(url, snippet)
                ));
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private List<WikiWebSearchResult> searchSearxng(WikiWebSearchConfig config, String query, int limit) {
        if (!StringUtils.hasText(config.instanceUrl())) {
            throw new BizException("SearXNG 实例地址未配置");
        }
        String base = config.instanceUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        Map<String, Object> response = RestClient.create().get()
                .uri(base + "/search?q={q}&format=json&categories={categories}",
                        query,
                        StringUtils.hasText(config.category()) ? config.category() : "general")
                .retrieve().body(Map.class);
        List<WikiWebSearchResult> results = new ArrayList<>();
        Object data = response == null ? null : response.get("results");
        if (data instanceof List<?> rows) {
            int count = 0;
            for (Object row : rows) {
                if (count++ >= limit) {
                    break;
                }
                if (!(row instanceof Map<?, ?> map)) {
                    continue;
                }
                String url = stringValue(map.get("url"));
                String content = stringValue(map.get("content"));
                results.add(new WikiWebSearchResult(
                        stringValue(map.get("title")),
                        url,
                        content,
                        StringUtils.hasText(content) ? content : fetchPageContent(url, content)
                ));
            }
        }
        return results;
    }

    private String fetchPageContent(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible; YudreamWikiBot/1.0)")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallback == null ? "" : fallback;
            }
            String text = stripHtml(response.body());
            if (text.isBlank()) {
                return fallback == null ? "" : fallback;
            }
            return text.length() > PAGE_CONTENT_LIMIT ? text.substring(0, PAGE_CONTENT_LIMIT) : text;
        }
        catch (Exception ignored) {
            return fallback == null ? "" : fallback;
        }
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'");
        return text.replaceAll("\\s+", " ").trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
