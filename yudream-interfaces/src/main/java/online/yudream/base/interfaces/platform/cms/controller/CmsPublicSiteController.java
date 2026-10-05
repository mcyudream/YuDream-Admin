package online.yudream.base.interfaces.platform.cms.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.service.CmsWorkspaceStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 公开静态站点服务：读站点级发布快照（最后发布者胜）。
 * 页面 URL = /api/public/cms/site/<path>（pages/index.html → 站点首页）；
 * HTML 响应注入 <base href> 使相对资产引用（assets/...）落到同前缀。
 */
@RestController
@RequestMapping("/api/public/cms")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class CmsPublicSiteController {

    private static final String SITE_BASE = "/api/public/cms/site/";
    private final CmsWorkspaceStore workspaceStore;

    @GetMapping(value = "/site/**", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> serve(jakarta.servlet.http.HttpServletRequest request) {
        String path = extractPath(request);
        if (path.isBlank() || path.endsWith("/")) {
            path = path + "index.html";
        }
        byte[] data = workspaceStore.readPublishedSite("pages/" + path);
        if (data == null && !path.endsWith(".html")) {
            data = workspaceStore.readPublishedSite("pages/" + path + "/index.html");
        }
        if (data == null) {
            data = workspaceStore.readPublishedSite(path);
        }
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        if (isHtml(path, data)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(injectBase(data));
        }
        return ResponseEntity.ok().contentType(contentTypeOf(path)).body(data);
    }

    /** 匿名运行时库：公开静态页无需登录即可接入数据注入。 */
    @GetMapping(value = "/lib.js", produces = "application/javascript;charset=UTF-8")
    public ResponseEntity<byte[]> lib() throws java.io.IOException {
        byte[] body = new ClassPathResource("cms/cms-lib.js").getInputStream().readAllBytes();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript;charset=UTF-8"))
                .header("Cache-Control", "public, max-age=300")
                .body(body);
    }

    private static String extractPath(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        String marker = "/site/";
        int index = uri.indexOf(marker);
        String path = index < 0 ? "" : uri.substring(index + marker.length());
        String query = request.getQueryString();
        return (query == null ? path : path + "?" + query).trim();
    }

    /** 相对资产引用统一锚定到站点前缀；lib.js 已是绝对路径不受影响。 */
    private static byte[] injectBase(byte[] html) {
        String text = new String(html, StandardCharsets.UTF_8);
        if (text.contains("<base ") || !text.contains("<head")) {
            return html;
        }
        return text.replaceFirst("(?i)<head[^>]*>",
                "<head><base href=\"" + SITE_BASE + "\">").getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isHtml(String path, byte[] data) {
        if (path.endsWith(".html") || path.endsWith(".htm")) {
            return true;
        }
        String head = new String(data, 0, Math.min(data.length, 256), StandardCharsets.UTF_8).toLowerCase();
        return head.contains("<!doctype html") || head.contains("<html");
    }

    private static MediaType contentTypeOf(String path) {
        String lower = path.toLowerCase();
        Map<String, MediaType> byExt = Map.ofEntries(
                Map.entry(".css", MediaType.valueOf("text/css;charset=UTF-8")),
                Map.entry(".js", MediaType.parseMediaType("application/javascript;charset=UTF-8")),
                Map.entry(".json", MediaType.APPLICATION_JSON),
                Map.entry(".png", MediaType.IMAGE_PNG),
                Map.entry(".jpg", MediaType.IMAGE_JPEG),
                Map.entry(".jpeg", MediaType.IMAGE_JPEG),
                Map.entry(".gif", MediaType.IMAGE_GIF),
                Map.entry(".webp", MediaType.valueOf("image/webp")),
                Map.entry(".svg", MediaType.valueOf("image/svg+xml")),
                Map.entry(".ico", MediaType.valueOf("image/x-icon")),
                Map.entry(".mp4", MediaType.valueOf("video/mp4")),
                Map.entry(".webm", MediaType.valueOf("video/webm")),
                Map.entry(".mp3", MediaType.valueOf("audio/mpeg")),
                Map.entry(".woff2", MediaType.valueOf("font/woff2")),
                Map.entry(".woff", MediaType.valueOf("font/woff")));
        return byExt.entrySet().stream()
                .filter(entry -> lower.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
