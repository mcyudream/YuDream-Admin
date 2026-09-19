package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.application.common.net.OutboundNetworkPolicy;
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
    private static final int MAX_REDIRECT_HOPS = 5;
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
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final OutboundNetworkPolicy outboundNetworkPolicy;

    public HttpWikiRemoteImageFetcher(OutboundNetworkPolicy outboundNetworkPolicy) {
        this.outboundNetworkPolicy = outboundNetworkPolicy;
    }

    @Override
    public WikiRemoteImage fetch(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException("图片地址不能为空");
        }
        URI target = outboundNetworkPolicy.validate(url, "图片", false);
        try {
            HttpResponse<byte[]> response = sendFollowingRedirects(target);
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
            String fileName = fileName(target);
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

    /** 手动跟随重定向，每一跳都重新执行出站目标校验。 */
    private HttpResponse<byte[]> sendFollowingRedirects(URI initial) throws Exception {
        URI current = initial;
        for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (compatible; YudreamWikiBot/1.0)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status >= 300 && status < 400 && hop < MAX_REDIRECT_HOPS) {
                current = outboundNetworkPolicy.validateRedirect(current,
                        response.headers().firstValue("Location").orElse(null), "图片");
                continue;
            }
            return response;
        }
        throw new BizException("图片重定向次数过多");
    }

    private String fileName(URI url) {
        String path = url.getPath();
        String name = path == null || path.isBlank() ? "" : path.substring(path.lastIndexOf('/') + 1);
        return name.isBlank() ? "image.png" : name;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
