package online.yudream.base.interfaces.platform.preview.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.preview.service.PluginPreviewFileOpener;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 平台签名公开文件端点：kkFileView 回源与浏览器直读共用。
 * 无登录注解，匿名可达，安全边界是 HMAC 短时效 token（由平台文件预览能力签发）；
 * 支持 Range 分段（kkFileView 与浏览器媒体标签会分段拉取）。
 */
@RestController
@RequestMapping("/api/public/preview")
@RequiredArgsConstructor
public class PublicFilePreviewController {

    private static final int BUFFER_SIZE = 8192;

    private final PluginPreviewFileOpener previewFileOpener;

    // 声明类型必须让 Spring 看到 StreamingResponseBody：ResponseEntity<?> 的通配符无法解析，
    // 会落到 HttpEntityMethodProcessor 因无消息转换器抛 HttpMessageNotWritableException（500）。
    @GetMapping("/file/{token}/{*filename}")
    public ResponseEntity<StreamingResponseBody> file(@PathVariable String token,
                                                      @PathVariable String filename,
                                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        Optional<PluginStoredFile> opened = previewFileOpener.openSignedFile(token);
        if (opened.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "签名无效、已过期或文件不存在");
        }
        PluginStoredFile file = opened.get();
        String displayName = displayName(filename, file.objectKey());
        MediaType mediaType = StringUtils.hasText(file.contentType())
                ? safeParse(file.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        long total = file.contentLength() == null ? -1L : file.contentLength();

        long[] window = resolveWindow(range, total);
        if (window == null) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMessage("Range 不合法"));
        }
        long start = window[0];
        long end = window[1];
        boolean partial = start > 0 || (total >= 0 && end < total - 1);

        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = file.inputStream()) {
                if (start > 0) {
                    inputStream.skipNBytes(start);
                }
                long remaining = end >= start ? end - start + 1 : Long.MAX_VALUE;
                byte[] buffer = new byte[BUFFER_SIZE];
                while (remaining > 0) {
                    int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) {
                        break;
                    }
                    outputStream.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        };

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20"));
        if (partial) {
            builder.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total);
        }
        long length = end >= start ? end - start + 1 : -1;
        if (length >= 0) {
            builder.contentLength(length);
        }
        return builder.body(body);
    }

    /** 解析 Range 为 [start, end]（闭区间）；无 Range 时为全量；非法返回 null。 */
    private static long[] resolveWindow(String range, long total) {
        if (!StringUtils.hasText(range) || !range.startsWith("bytes=")) {
            return new long[]{0L, total >= 0 ? total - 1 : Long.MAX_VALUE};
        }
        if (total <= 0) {
            return null;
        }
        String spec = range.substring("bytes=".length()).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            long start = spec.substring(0, dash).isEmpty()
                    ? Math.max(0L, total - Long.parseLong(spec.substring(dash + 1).trim()))
                    : Long.parseLong(spec.substring(0, dash).trim());
            long end = spec.substring(dash + 1).isEmpty() || spec.substring(0, dash).isEmpty()
                    ? total - 1
                    : Long.parseLong(spec.substring(dash + 1).trim());
            if (start > end || start >= total) {
                return null;
            }
            return new long[]{start, Math.min(end, total - 1)};
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static String displayName(String filename, String objectKey) {
        String name = filename == null ? "" : filename.trim();
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (!name.isEmpty()) {
            return name;
        }
        int slash = objectKey == null ? -1 : objectKey.lastIndexOf('/');
        return slash >= 0 ? objectKey.substring(slash + 1) : "file";
    }

    private static MediaType safeParse(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        }
        catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static ResponseEntity<StreamingResponseBody> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(jsonMessage(message));
    }

    /** 错误分支同样必须是 StreamingResponseBody，与方法的声明返回类型保持一致。 */
    private static StreamingResponseBody jsonMessage(String message) {
        byte[] bytes = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return outputStream -> outputStream.write(bytes);
    }
}
