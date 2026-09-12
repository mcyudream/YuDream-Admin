package online.yudream.base.interfaces.platform.plugin.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginMarketPublicationAppService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 自托管市场源只读契约端点（匿名）。与消费端 JdkPluginStoreGateway 的 schemaVersion=1 契约同构：
 * 根 index.json → 每插件 index.json → 每版本 descriptor.json / plugin.jar。
 * 相对引用均相对各 index.json 所在目录，且位于 /api/public/plugin-market/ 之内，满足同源同路径校验。
 */
@RestController
@RequestMapping("/api/public/plugin-market")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.plugin-market-source", name = "enabled", havingValue = "true")
public class PublicPluginMarketController {

    private final PluginMarketPublicationAppService pluginMarketPublicationAppService;

    @GetMapping("/index.json")
    public ResponseEntity<String> rootIndex() {
        return json(pluginMarketPublicationAppService.rootIndexJson());
    }

    @GetMapping("/{code}/index.json")
    public ResponseEntity<String> codeIndex(@PathVariable String code) {
        return pluginMarketPublicationAppService.codeIndexJson(code)
                .map(this::json)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/{pluginVersion}/descriptor.json")
    public ResponseEntity<String> descriptor(@PathVariable String code, @PathVariable String pluginVersion) {
        return pluginMarketPublicationAppService.publishedDescriptorJson(code, pluginVersion)
                .<ResponseEntity<String>>map(body -> ResponseEntity.ok()
                        .cacheControl(immutableCache())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/{pluginVersion}/plugin.jar")
    public ResponseEntity<FileSystemResource> jar(@PathVariable String code, @PathVariable String pluginVersion) {
        return pluginMarketPublicationAppService.incrementDownloadAndResolveLegacyJar(code, pluginVersion)
                .<ResponseEntity<FileSystemResource>>map(path -> ResponseEntity.ok()
                        .cacheControl(immutableCache())
                        .contentType(MediaType.parseMediaType("application/java-archive"))
                        .contentLength(path.toFile().length())
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** {code}@{version} 不可覆盖，descriptor/JAR 可按不可变资源缓存。 */
    private CacheControl immutableCache() {
        return CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
    }
}
