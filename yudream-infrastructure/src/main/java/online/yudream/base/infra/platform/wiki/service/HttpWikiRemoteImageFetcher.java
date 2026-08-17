package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.service.WikiRemoteImageFetcher;
import online.yudream.base.domain.platform.wiki.valobj.WikiRemoteImage;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * 远程图片抓取实现：HTTP GET 下载图片字节，限制大小与类型。
 */
@Service
public class HttpWikiRemoteImageFetcher implements WikiRemoteImageFetcher {

    private static final long MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.of(
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".gif", "image/gif",
            ".webp", "image/webp",
            ".svg", "image/svg+xml",
            ".bmp", "image/bmp",
            ".avif", "image/avif"
    );
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public WikiRemoteImage fetch(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException("图片地址不能为空");
        }
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BizException("仅支持 http/https 图片地址");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalized))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (compatible; YudreamWikiBot/1.0)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers
                    .ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("图片下载失败：HTTP " + response.statusCode());
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new BizException("图片内容为空");
            }
            if (body.length > MAX_IMAGE_BYTES) {
                throw new BizException("图片超过大小限制（8MB）");
            }
            String contentType = response.headers().firstValue("Content-Type")
                    .map(value -> value.split(";")[0].trim().toLowerCase(Locale.ROOT))
                    .orElse("");
            String fileName = fileName(normalized);
            if (!contentType.startsWith("image/")) {
                contentType = EXTENSION_CONTENT_TYPES.getOrDefault(extension(fileName), "");
            }
            if (contentType.isBlank()) {
                throw new BizException("无法识别图片类型");
            }
            return new WikiRemoteImage(body, contentType, fileName);
        }
        catch (BizException exception) {
            throw exception;
        }
        catch (Exception exception) {
            String message = exception.getMessage();
            throw new BizException("图片下载失败：" + (message == null || message.isBlank() ? exception.getClass().getSimpleName() : message));
        }
    }

    private String fileName(String url) {
        String path = URI.create(url).getPath();
        String name = path == null || path.isBlank() ? "" : path.substring(path.lastIndexOf('/') + 1);
        return name.isBlank() ? "image.png" : name;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
