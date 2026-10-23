package online.yudream.base.application.platform.interop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mc-interop v1 well-known 聚合出口（协议 §2/§7）。
 *
 * <p>本站条目来自 mc-interop 插件出口（进程内调度）；peers 声明的友站清单按
 * §7.2 互采合并：条目携带 source 溯源、与本站/已合并条目 id 冲突即丢弃、
 * 单个友站失败只跳过不影响整体。聚合结果缓存 5 分钟，ETag 由内容摘要派生。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McInteropWellKnownAppService {

    private static final String PLUGIN_CODE = "mc-interop";
    private static final String PLUGIN_PATH = "/interop/v1/site";
    private static final long CACHE_TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final int MAX_PEER_MANIFEST_BYTES = 1024 * 1024;

    private final PluginAppService pluginAppService;
    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile Cached cached;

    private record Cached(long at, String etag, Map<String, Object> document, String json) {
    }

    public record WellKnownResponse(int status, String etag, Object body) {
    }

    /** well-known 出口：条件请求返回 304，否则返回聚合文档（JSON 文本，数字类型保真）。 */
    public WellKnownResponse site(String ifNoneMatch) {
        Cached current = current();
        if (current.etag().equals(ifNoneMatch)) {
            return new WellKnownResponse(304, current.etag(), null);
        }
        return new WellKnownResponse(200, current.etag(), current.json());
    }

    private Cached current() {
        Cached snapshot = cached;
        if (snapshot != null && System.currentTimeMillis() - snapshot.at() < CACHE_TTL_MILLIS) {
            return snapshot;
        }
        synchronized (this) {
            if (cached != null && System.currentTimeMillis() - cached.at() < CACHE_TTL_MILLIS) {
                return cached;
            }
            Map<String, Object> document = fetchLocalDocument();
            mergePeers(document);
            // 以插件/聚合 Map 直接序列化为 JSON 文本返回：宿主全局 ObjectMapper 的
            // Long→String 定制会破坏协议要求的 size/revision 整数类型。
            String json;
            String etag;
            try {
                json = objectMapper.writeValueAsString(document);
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                        json.getBytes(StandardCharsets.UTF_8));
                etag = "\"" + HexFormat.of().formatHex(digest).substring(0, 32) + "\"";
            } catch (Exception e) {
                throw new IllegalStateException("mc-interop 出口序列化失败", e);
            }
            Cached next = new Cached(System.currentTimeMillis(), etag, document, json);
            cached = next;
            return next;
        }
    }

    /** 本站出口：进程内调度 mc-interop 插件端点；503（未配置）时输出空壳文档。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchLocalDocument() {
        try {
            PluginHttpDispatchCmd cmd = new PluginHttpDispatchCmd();
            cmd.setPluginCode(PLUGIN_CODE);
            cmd.setMethod("GET");
            cmd.setPath(PLUGIN_PATH);
            cmd.setHeaders(Map.of());
            cmd.setQuery(Map.of());
            cmd.setParts(Map.of());
            cmd.setPermissions(List.of());
            PluginHttpDispatchDTO result = pluginAppService.dispatch(cmd);
            if (result.getStatus() != 200) {
                log.warn("mc-interop 插件出口未就绪：status={}", result.getStatus());
                return emptyDocument();
            }
            // 插件以自序列化 JSON 字节透传（保 size/revision 数字类型）；兼容 Map/String 形态
            if (result.getBody() instanceof byte[] bytes) {
                return objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), Map.class);
            }
            if (result.getBody() instanceof String text) {
                return objectMapper.readValue(text, Map.class);
            }
            if (result.getBody() instanceof Map) {
                return objectMapper.convertValue(result.getBody(), Map.class);
            }
            return emptyDocument();
        } catch (Exception e) {
            log.warn("调度 mc-interop 插件出口失败：{}", e.getMessage());
            return emptyDocument();
        }
    }

    private void mergePeers(Map<String, Object> document) {
        Object peersValue = document.get("peers");
        if (!(peersValue instanceof List<?> peers) || peers.isEmpty()) {
            return;
        }
        Object packsValue = document.get("packs");
        List<Map<String, Object>> packs = packsValue instanceof List<?> list
                ? new ArrayList<>((List<Map<String, Object>>) (List<?>) list)
                : new ArrayList<>();
        var seen = ConcurrentHashMap.<String>newKeySet();
        for (Map<String, Object> pack : packs) {
            seen.add(String.valueOf(pack.get("id")));
        }
        for (Object peerValue : peers) {
            if (!(peerValue instanceof Map<?, ?> peer)) {
                continue;
            }
            String siteId = text(peer.get("siteId"));
            String manifestUrl = text(peer.get("manifestUrl"));
            if (siteId == null || manifestUrl == null || !manifestUrl.startsWith("https://")) {
                continue;
            }
            try {
                mergeOnePeer(packs, seen, siteId, text(peer.get("domain")), manifestUrl);
            } catch (Exception e) {
                // 协议 §7.2：友站失败只跳过，不影响本站出口
                log.info("互采友站 {} 失败：{}", siteId, e.getMessage());
            }
        }
        document.put("packs", packs);
    }

    @SuppressWarnings("unchecked")
    private void mergeOnePeer(List<Map<String, Object>> packs, java.util.Set<String> seen,
                              String siteId, String domain, String manifestUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(manifestUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body().length > MAX_PEER_MANIFEST_BYTES) {
            return;
        }
        Map<String, Object> remote = objectMapper.readValue(
                new String(response.body(), StandardCharsets.UTF_8), Map.class);
        if (!(remote.get("packs") instanceof List<?> remotePacks)) {
            return;
        }
        for (Object packValue : remotePacks) {
            if (!(packValue instanceof Map)) {
                continue;
            }
            Map<String, Object> pack = objectMapper.convertValue(packValue, Map.class);
            String id = text(pack.get("id"));
            if (id == null || !seen.add(id)) {
                // 与本站或已合并条目 id 冲突：丢弃该外来条目（§7.2）
                continue;
            }
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("siteId", siteId);
            source.put("domain", domain == null ? "" : domain);
            pack.put("source", source);
            packs.add(pack);
        }
    }

    private static Map<String, Object> emptyDocument() {
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("id", "");
        site.put("name", "");
        site.put("description", "");
        site.put("domain", "");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("site", site);
        document.put("packs", new ArrayList<>());
        document.put("peers", new ArrayList<>());
        document.put("updatedAt", "1970-01-01T00:00:00Z");
        return document;
    }

    private static String etagOf(Map<String, Object> document) {
        try {
            byte[] bytes = objectMapperStaticWrite(document);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return "\"" + HexFormat.of().formatHex(digest).substring(0, 32) + "\"";
        } catch (Exception e) {
            return "\"mc-interop\"";
        }
    }

    private static byte[] objectMapperStaticWrite(Map<String, Object> document) {
        StringBuilder builder = new StringBuilder();
        appendValue(builder, document);
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder builder, Object value) {
        switch (value) {
            case null -> builder.append("null");
            case String text -> appendEscaped(builder, text);
            case Number number -> builder.append(number);
            case Boolean flag -> builder.append(flag);
            case Map<?, ?> map -> {
                builder.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    appendEscaped(builder, String.valueOf(entry.getKey()));
                    builder.append(':');
                    appendValue(builder, entry.getValue());
                }
                builder.append('}');
            }
            case List<?> list -> {
                builder.append('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    appendValue(builder, item);
                }
                builder.append(']');
            }
            default -> appendEscaped(builder, String.valueOf(value));
        }
    }

    private static void appendEscaped(StringBuilder builder, String value) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static String text(Object value) {
        Object resolved = value;
        return resolved == null ? null : String.valueOf(resolved);
    }
}
