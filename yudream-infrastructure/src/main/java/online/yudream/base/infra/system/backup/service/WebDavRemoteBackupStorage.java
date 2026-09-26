package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.service.RemoteBackupStorage;
import online.yudream.base.domain.system.backup.valobj.RemoteEntry;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * WebDAV 异地备份存储（reactor-netty HttpClient，PROPFIND/MKCOL/PUT/GET/DELETE）。
 * 目标开启「跳过 TLS 证书校验」时使用 InsecureTrustManagerFactory——netty 传输层
 * 不做主机名校验，可确定性地支持自签名证书与 IP 直连；关闭时走默认信任链。
 * 连接为短生命周期语义：每次操作独立请求，不持长驻资源。
 */
@Slf4j
public class WebDavRemoteBackupStorage implements RemoteBackupStorage {
    private static final int TIMEOUT_MILLIS = 60_000;

    private final RemoteTarget target;
    private final HttpClient http;
    /** 服务端不支持 PROPFIND（405/501，如 Nginx dav 模块）时降级：列表返回空、上传/下载仍可用。 */
    private volatile boolean listDisabled;

    WebDavRemoteBackupStorage(RemoteTarget target) {
        this.target = target;
        HttpClient base = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(Duration.ofMillis(TIMEOUT_MILLIS));
        if (target.isInsecureTls()) {
            try {
                SslContext sslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                // IP 直连自签证书场景：握手前清空 SNI 匹配器（IP 不是合法 SNI 名，避免 SNI 解析异常），
                // 并信任所有证书——两者合起来才构成确定性的「跳过 TLS 校验」。
                base = base.secure(spec -> spec.sslContext(sslContext)
                        .handlerConfigurator(handler -> handler.engine()
                                .setSNIMatchers(java.util.Collections.<javax.net.ssl.SNIServerName>emptyList())));
            } catch (Exception e) {
                throw new BizException("初始化 TLS 上下文失败：" + e.getMessage());
            }
        }
        this.http = base;
    }

    /** 构建带认证与 Depth 头的单次请求客户端。 */
    private HttpClient client(String depth) {
        return http.headers(headers -> {
            applyAuth(headers::set);
            if (depth != null) {
                headers.set("Depth", depth);
            }
        });
    }

    private static SSLContext trustAllContext() {
        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        }};
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new java.security.SecureRandom());
            return context;
        } catch (Exception e) {
            throw new BizException("初始化 TLS 上下文失败：" + e.getMessage());
        }
    }

    @Override
    public void test() {
        int code = exchange("PROPFIND", baseDirUrl(), propfindBody(), "0");
        if (code == 401 || code == 403) {
            throw new BizException("WebDAV 认证失败，请检查账号与密码");
        }
        if (code == 404) {
            throw new BizException("WebDAV 远端目录不存在：" + target.getBasePath());
        }
        if (code == 405 || code == 501) {
            // 服务端不实现 PROPFIND（如 Nginx dav 模块/极简 WebDAV）：降级为仅上传/下载，
            // 远端列表与保留份数清理不可用；备份推送不受影响。
            listDisabled = true;
            log.warn("WebDAV 目标 {} 不支持 PROPFIND（{}），已降级：可上传/下载，远端列表与自动清理不可用",
                    target.getCode(), code);
            return;
        }
        if (code != 207 && code != 200) {
            throw new BizException("WebDAV 探测失败，远端返回 " + code
                    + "——该地址可能未启用 WebDAV（如指向了普通网站或 S3 端点），请确认服务端与基础路径");
        }
    }

    @Override
    public void put(String path, InputStream in, long size) {
        try {
            ensureRemoteDirs(path);
            Integer code = client(null)
                    .headers(headers -> headers.set("Content-Type", "application/octet-stream"))
                    .request(HttpMethod.PUT)
                    .uri(fileUrl(path))
                    .send(reactor.core.publisher.Flux.generate(
                            () -> in,
                            (input, sink) -> {
                                byte[] buffer = new byte[64 * 1024];
                                try {
                                    int read = input.read(buffer);
                                    if (read < 0) {
                                        sink.complete();
                                    } else {
                                        sink.next(io.netty.buffer.Unpooled.wrappedBuffer(buffer, 0, read));
                                    }
                                } catch (java.io.IOException e) {
                                    sink.error(e);
                                }
                                return input;
                            }))
                    .responseSingle((response, byteBufFlux) -> Mono.just(response.status().code()))
                    .block(Duration.ofMillis(TIMEOUT_MILLIS));
            requireSuccess(code == null ? 0 : code, "上传备份");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 上传失败：" + describe(e));
        }
    }

    @Override
    public List<RemoteEntry> list(String dir) {
        if (listDisabled) {
            return List.of();
        }
        return propfindDir(dir).files();
    }

    @Override
    public List<String> listDirs(String dir) {
        if (listDisabled) {
            return List.of();
        }
        return propfindDir(dir).dirs();
    }

    private PropfindResult propfindDir(String dir) {
        if (listDisabled) {
            return new PropfindResult(List.of(), List.of());
        }
        String dirPath = normalizeDir(target.getBasePath() + "/"
                + (dir == null || dir.isBlank() ? "" : dir.startsWith("/") ? dir.substring(1) : dir));
        Tuple2<Integer, String> result;
        try {
            result = client("1")
                    .request(HttpMethod.valueOf("PROPFIND"))
                    .uri(dirUrl(dir))
                    .send(ByteBufFlux.fromString(Mono.just(propfindBody())))
                    .responseSingle((response, byteBufMono) -> byteBufMono.asString(StandardCharsets.UTF_8)
                            .defaultIfEmpty("")
                            .map(body -> Tuples.of(response.status().code(), body)))
                    .block(Duration.ofMillis(TIMEOUT_MILLIS));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 列表失败：" + describe(e));
        }
        if (result == null) {
            throw new BizException("远端无响应");
        }
        int code = result.getT1();
        String xml = result.getT2();
        if (code == 401 || code == 403) {
            throw new BizException("WebDAV 认证失败，请检查账号与密码");
        }
        if (code == 404) {
            return new PropfindResult(List.of(), List.of());
        }
        if (code == 405 || code == 501) {
            listDisabled = true;
            log.warn("WebDAV 目标 {} 返回 {}（不支持目录列举），已降级", target.getCode(), code);
            return new PropfindResult(List.of(), List.of());
        }
        if (code != 207 && code != 200) {
            throw new BizException("WebDAV 列表失败，远端返回 " + code + "，响应片段：" + snippet(xml));
        }
        return parseListing(xml, dirPath);
    }

    private record PropfindResult(List<RemoteEntry> files, List<String> dirs) {
    }

    private String propfindBody() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<D:propfind xmlns:D=\"DAV:\"><D:prop><D:displayname/><D:getcontentlength/>"
                + "<D:getlastmodified/><D:resourcetype/></D:prop></D:propfind>";
    }

    @Override
    public void fetch(String path, Path destination) {
        Tuple2<HttpClientResponse, Connection> result;
        try {
            result = client(null)
                    .request(io.netty.handler.codec.http.HttpMethod.GET)
                    .uri(fileUrl(path))
                    .responseConnection((response, connection) -> Mono.just(Tuples.of(response, connection)))
                    .blockLast(Duration.ofMillis(TIMEOUT_MILLIS));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 下载失败：" + describe(e));
        }
        if (result == null) {
            throw new BizException("远端无响应");
        }
        HttpClientResponse response = result.getT1();
        Connection connection = result.getT2();
        try {
            if (response.status().code() == 404) {
                throw new BizException("远端备份文件不存在：" + path);
            }
            requireSuccess(response.status().code(), "下载备份");
            // 分块流式落盘，避免大归档整档进内存
            try (java.io.OutputStream out = Files.newOutputStream(destination,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE)) {
                connection.inbound().receive().asInputStream()
                        .toStream(8)
                        .forEach(chunk -> {
                            try {
                                chunk.transferTo(out);
                            } catch (java.io.IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        });
                out.flush();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 下载失败：" + describe(e));
        } finally {
            connection.dispose();
        }
    }

    @Override
    public void delete(String path) {
        try {
            Integer code = client(null)
                    .request(HttpMethod.DELETE)
                    .uri(fileUrl(path))
                    .responseSingle((response, byteBufFlux) -> Mono.just(response.status().code()))
                    .block(Duration.ofMillis(TIMEOUT_MILLIS));
            int value = code == null ? 0 : code;
            if (value != 404 && (value < 200 || value >= 300)) {
                throw new BizException("WebDAV 删除失败，远端返回 " + value);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 删除失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 请求封装

    private int exchange(String method, String url, String body, String depth) {
        try {
            Integer code = client(depth)
                    .request(HttpMethod.valueOf(method))
                    .uri(url)
                    .send(ByteBufFlux.fromString(Mono.just(body)))
                    .responseSingle((response, byteBufFlux) -> Mono.just(response.status().code()))
                    .block(Duration.ofMillis(TIMEOUT_MILLIS));
            return code == null ? 0 : code;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 连接失败：" + describe(e));
        }
    }

    private void applyAuth(BiConsumer<String, String> headers) {
        if (target.getUsername() != null && !target.getUsername().isBlank()) {
            String token = Base64.getEncoder().encodeToString(
                    (target.getUsername() + ":" + (target.getPassword() == null ? "" : target.getPassword()))
                            .getBytes(StandardCharsets.UTF_8));
            headers.accept("Authorization", "Basic " + token);
        }
        headers.accept("User-Agent", "YuDreamAdmin-Backup");
    }

    private void requireSuccess(int code, String action) {
        if (code == 401 || code == 403) {
            throw new BizException(action + "失败：WebDAV 认证被拒绝");
        }
        if (code < 200 || code >= 300) {
            throw new BizException(action + "失败，远端返回 " + code);
        }
    }

    private void ensureRemoteDirs(String path) throws Exception {
        String dirPath = normalizeDir(target.getBasePath() + "/"
                + (path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ""));
        StringBuilder current = new StringBuilder();
        for (String segment : dirPath.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            current.append('/').append(segment);
            int code = exchange("MKCOL", schemeHost() + encodePath(current + "/"), null, null);
            if (code == 401 || code == 403) {
                throw new BizException("WebDAV 认证失败，无法创建目录：" + current);
            }
            // 405/301 等视为已存在，留给后续 PUT 校验
        }
    }

    // ---------------------------------------------------------------- URL 与解析

    private String schemeHost() {
        return target.webDavScheme() + "://" + target.getHost() + ":" + target.getPort();
    }

    private String normalizeDir(String path) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private String baseDirUrl() {
        return schemeHost() + encodePath(normalizeDir(target.getBasePath()));
    }

    private String dirUrl(String dir) {
        String normalized = normalizeDir(target.getBasePath() + "/"
                + (dir == null || dir.isBlank() ? "" : dir.startsWith("/") ? dir.substring(1) : dir));
        return schemeHost() + encodePath(normalized);
    }

    private String fileUrl(String path) {
        String normalized = normalizeDir(target.getBasePath())
                + (path.startsWith("/") ? path.substring(1) : path);
        return schemeHost() + encodePath(normalized);
    }

    private String encodePath(String path) {
        StringBuilder builder = new StringBuilder();
        for (String segment : path.split("/")) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return builder.toString();
    }

    private static String decodedPath(String href) {
        String path = href;
        int scheme = href.indexOf("://");
        if (scheme >= 0) {
            int pathStart = href.indexOf('/', scheme + 3);
            path = pathStart >= 0 ? href.substring(pathStart) : "/";
        }
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    private static String textOfFirstChild(Element parent, String localName) {
        NodeList children = parent.getElementsByTagNameNS("*", localName);
        if (children.getLength() == 0) {
            return null;
        }
        Node child = children.item(0);
        String text = child.getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** 响应体摘要（去标签截 120 字），用于失败信息定位。 */
    private static String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 120);
    }

    /** 异常消息带类名：TLS/网络各层异常文本相近，类名便于定位问题层。 */
    private static String describe(Exception e) {
        String message = e.getMessage();
        String text = message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
        return e.getClass().getSimpleName() + ": " + text;
    }

    private PropfindResult parseListing(String xml, String dirPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))))
                    .getDocumentElement();
            List<RemoteEntry> files = new ArrayList<>();
            List<String> dirs = new ArrayList<>();
            NodeList responses = root.getElementsByTagNameNS("*", "response");
            for (int i = 0; i < responses.getLength(); i++) {
                Element response = (Element) responses.item(i);
                String href = textOfFirstChild(response, "href");
                if (href == null) {
                    continue;
                }
                String decodedPath = decodedPath(href);
                boolean isCollection = response.getElementsByTagNameNS("*", "collection").getLength() > 0
                        || decodedPath.endsWith("/");
                String trimmed = decodedPath.endsWith("/")
                        ? decodedPath.substring(0, decodedPath.length() - 1)
                        : decodedPath;
                String name = trimmed.substring(trimmed.lastIndexOf('/') + 1);
                if (name.isBlank()) {
                    continue;
                }
                if (isCollection) {
                    if (!decodedPath.equals(dirPath) && !dirs.contains(name)) {
                        dirs.add(name);
                    }
                    continue;
                }
                String parent = decodedPath.substring(0, decodedPath.lastIndexOf('/') + 1);
                if (!parent.equals(dirPath)) {
                    continue;
                }
                long size = 0;
                String length = textOfFirstChild(response, "getcontentlength");
                if (length != null) {
                    try {
                        size = Long.parseLong(length.trim());
                    } catch (NumberFormatException ignored) {
                        // 个别服务器不返回 content-length
                    }
                }
                Long modified = null;
                String lastModified = textOfFirstChild(response, "getlastmodified");
                if (lastModified != null) {
                    try {
                        modified = ZonedDateTime.parse(lastModified,
                                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                    } catch (Exception ignored) {
                        // 时间格式异常时保持空
                    }
                }
                files.add(new RemoteEntry(name, size, modified));
            }
            return new PropfindResult(files, dirs);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("解析 WebDAV 响应失败：" + e.getMessage());
        }
    }
}
