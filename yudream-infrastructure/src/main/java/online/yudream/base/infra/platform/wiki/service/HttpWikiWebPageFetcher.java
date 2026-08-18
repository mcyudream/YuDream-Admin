package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.service.WikiWebPageFetcher;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebPage;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页抓取实现：HTTP GET + 轻量 HTML 清洗（类似 Readability 的极简替代，用于网页剪藏与 URL 导入）。
 */
@Service
public class HttpWikiWebPageFetcher implements WikiWebPageFetcher {

    private static final int MAX_CONTENT_LENGTH = 100_000;
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public WikiWebPage fetch(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException("网页地址不能为空");
        }
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BizException("仅支持 http/https 网页地址");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalized))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (compatible; YudreamWikiBot/1.0)")
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("网页抓取失败：HTTP " + response.statusCode());
            }
            String html = response.body();
            String title = extractTitle(html);
            String content = stripHtml(html);
            return new WikiWebPage(title == null ? normalized : title, limit(content), "text/html");
        }
        catch (BizException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new BizException("网页抓取失败：" + readableMessage(exception));
        }
    }

    private String extractTitle(String html) {
        if (html == null) {
            return null;
        }
        Matcher matcher = TITLE.matcher(html);
        if (matcher.find()) {
            return stripHtml(matcher.group(1));
        }
        return null;
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<(head|nav|footer|aside)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limit(String content) {
        return content == null ? "" : (content.length() > MAX_CONTENT_LENGTH ? content.substring(0, MAX_CONTENT_LENGTH) : content);
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
