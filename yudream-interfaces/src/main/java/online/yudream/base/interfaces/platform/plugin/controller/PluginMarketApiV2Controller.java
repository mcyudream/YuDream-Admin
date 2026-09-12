package online.yudream.base.interfaces.platform.plugin.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginMarketPublicationAppService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Market Source Protocol v2：交互式自托管市场源的正式只读协议（裸 JSON，无 Result 包裹）。
 * 其他实例以 V2_API 类型添加 `https://host/api/public/plugin-market` 为源即接入；
 * 协议规范见 yd-docs「自托管市场源」。
 */
@RestController
@RequestMapping("/api/public/plugin-market/api/v2")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.plugin-market-source", name = "enabled", havingValue = "true")
public class PluginMarketApiV2Controller {

    private final PluginMarketPublicationAppService pluginMarketPublicationAppService;

    @GetMapping("/manifest")
    public ResponseEntity<String> manifest() {
        return json(pluginMarketPublicationAppService.manifestJson());
    }

    @GetMapping("/categories")
    public ResponseEntity<String> categories() {
        return json(pluginMarketPublicationAppService.categoriesJson());
    }

    @GetMapping("/tags")
    public ResponseEntity<String> tags(@RequestParam(value = "limit", defaultValue = "30") int limit) {
        return json(pluginMarketPublicationAppService.tagsJson(limit));
    }

    @GetMapping("/plugins")
    public ResponseEntity<String> plugins(@RequestParam(value = "search", required = false) String search,
                                          @RequestParam(value = "categories", required = false) String categories,
                                          @RequestParam(value = "tags", required = false) String tags,
                                          @RequestParam(value = "authorId", required = false) Long authorId,
                                          @RequestParam(value = "publishedAfter", required = false) String publishedAfter,
                                          @RequestParam(value = "publishedBefore", required = false) String publishedBefore,
                                          @RequestParam(value = "sort", required = false) String sort,
                                          @RequestParam(value = "page", defaultValue = "1") int page,
                                          @RequestParam(value = "size", defaultValue = "20") int size) {
        return json(pluginMarketPublicationAppService.pagePluginsJson(search, categories, tags, authorId,
                publishedAfter, publishedBefore, sort, page, size));
    }

    @GetMapping("/plugins/{code}")
    public ResponseEntity<String> pluginDetail(@PathVariable String code) {
        return pluginMarketPublicationAppService.pluginDetailJson(code)
                .map(this::json)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/plugins/{code}/versions/{pluginVersion}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String code,
                                                       @PathVariable String pluginVersion) {
        return pluginMarketPublicationAppService.downloadPublication(code, pluginVersion)
                .<ResponseEntity<FileSystemResource>>map(path -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/java-archive"))
                        .contentLength(path.toFile().length())
                        .header("Content-Disposition", ContentDisposition.attachment()
                                .filename(code + "-" + pluginVersion + ".jar", StandardCharsets.UTF_8)
                                .build().toString())
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
