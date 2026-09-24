package online.yudream.base.infra.system.about;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.system.about.service.LatestVersionGateway;
import online.yudream.base.domain.system.about.valobj.LatestVersionProbe;
import online.yudream.base.domain.system.about.valobj.LatestVersionTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nexus 最新版本探测：Maven 走 maven-metadata.xml，npm 走包元数据 JSON 的 dist-tags.latest。
 * 单个目标失败降级为带原因的探测结果；带 TTL 缓存，页面刷新不会反复打 Nexus。
 * 基址出域校验由应用层在调用前完成（OutboundUrlGuard），本类只拼接固定形态的构件路径。
 */
@Service
public class NexusLatestVersionGateway implements LatestVersionGateway {

    private static final Logger log = LoggerFactory.getLogger(NexusLatestVersionGateway.class);
    private static final Pattern MAVEN_COORDINATE = Pattern.compile("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+");
    private static final Pattern NPM_PACKAGE = Pattern.compile("(@[A-Za-z0-9_.-]+/)?[A-Za-z0-9_.-]+");
    private static final Pattern XML_LATEST = Pattern.compile("<latest>([^<]+)</latest>");
    private static final Pattern XML_RELEASE = Pattern.compile("<release>([^<]+)</release>");
    private static final Pattern XML_VERSION = Pattern.compile("<version>([^<]+)</version>");

    private final AboutVersionCheckProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public NexusLatestVersionGateway(AboutVersionCheckProperties properties, ObjectMapper objectMapper) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMillis())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), objectMapper);
    }

    public NexusLatestVersionGateway(AboutVersionCheckProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<LatestVersionProbe> probe(List<LatestVersionTarget> targets) {
        List<LatestVersionProbe> result = new ArrayList<>();
        for (LatestVersionTarget target : targets) {
            result.add(probeOne(target));
        }
        return List.copyOf(result);
    }

    private LatestVersionProbe probeOne(LatestVersionTarget target) {
        String cacheKey = target.kind() + ":" + target.coordinate();
        CacheEntry hit = cache.get(cacheKey);
        if (hit != null && !hit.expired()) {
            return hit.probe();
        }
        LatestVersionProbe probe = doProbe(target);
        cache.put(cacheKey, new CacheEntry(probe, Instant.now().plusSeconds(Math.max(30, properties.getCacheTtlSeconds()))));
        return probe;
    }

    private LatestVersionProbe doProbe(LatestVersionTarget target) {
        String base = properties.getNexusBaseUrl();
        if (!StringUtils.hasText(base)) {
            return failure(target, null, "未配置 Nexus 基址");
        }
        URI uri;
        try {
            uri = artifactUri(base.replaceAll("/+$", ""), target);
        } catch (IllegalArgumentException e) {
            return failure(target, null, "构件坐标非法");
        }
        String url = uri.toString();
        try {
            String body = readText(uri);
            String latest = target.kind() == LatestVersionTarget.Kind.MAVEN
                    ? parseMavenLatest(body) : parseNpmLatest(body);
            if (!StringUtils.hasText(latest)) {
                return failure(target, url, "仓库响应中未找到最新版本号");
            }
            return new LatestVersionProbe(target.key(), target.name(), target.kind(), latest, url, Instant.now(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(target, url, "探测被中断");
        } catch (Exception e) {
            log.debug("最新版本探测失败 {} {}: {}", target.kind(), target.coordinate(), e.getMessage());
            return failure(target, url, "仓库不可达或响应异常");
        }
    }

    private URI artifactUri(String base, LatestVersionTarget target) {
        if (target.kind() == LatestVersionTarget.Kind.MAVEN) {
            if (!MAVEN_COORDINATE.matcher(target.coordinate()).matches()) {
                throw new IllegalArgumentException("非法 Maven 坐标");
            }
            String[] parts = target.coordinate().split(":", 2);
            return URI.create(base + "/repository/maven-public/"
                    + parts[0].replace('.', '/') + "/" + parts[1] + "/maven-metadata.xml");
        }
        if (!NPM_PACKAGE.matcher(target.coordinate()).matches()) {
            throw new IllegalArgumentException("非法 npm 包名");
        }
        // scoped 包名按 npm 协议编码斜杠（@yudream/plugin-sdk -> @yudream%2Fplugin-sdk）
        return URI.create(base + "/repository/npm-public/" + target.coordinate().replace("/", "%2F"));
    }

    private String readText(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMillis(Math.max(1, properties.getRequestTimeoutMillis())))
                .header("Accept", "*/*")
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            long maxBytes = Math.max(4_096, properties.getMaxResponseBytes());
            return new String(readLimited(body, maxBytes), StandardCharsets.UTF_8);
        }
    }

    /** maven-metadata：优先 versioning/latest，回落 release，再回落版本列表末位（Maven 元数据按时间升序）。 */
    private String parseMavenLatest(String xml) {
        Matcher latest = XML_LATEST.matcher(xml);
        if (latest.find()) {
            return latest.group(1).trim();
        }
        Matcher release = XML_RELEASE.matcher(xml);
        if (release.find()) {
            return release.group(1).trim();
        }
        String last = null;
        Matcher versions = XML_VERSION.matcher(xml);
        while (versions.find()) {
            last = versions.group(1).trim();
        }
        return last;
    }

    private String parseNpmLatest(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode distTags = root == null ? null : root.get("dist-tags");
        JsonNode latest = distTags == null ? null : distTags.get("latest");
        return latest != null && latest.isTextual() ? latest.textValue() : null;
    }

    private byte[] readLimited(InputStream input, long maxBytes) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("响应超过大小上限");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private LatestVersionProbe failure(LatestVersionTarget target, String url, String error) {
        return new LatestVersionProbe(target.key(), target.name(), target.kind(), null, url, Instant.now(), error);
    }

    private record CacheEntry(LatestVersionProbe probe, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
