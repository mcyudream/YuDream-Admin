package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.service.RemoteBackupStorage;
import online.yudream.base.domain.system.backup.valobj.RemoteEntry;
import org.springframework.http.HttpStatusCode;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * WebDAV 异地备份存储（JDK HttpClient，PROPFIND/MKCOL/PUT/GET/DELETE）。
 * 连接为短生命周期：每次操作独立请求，不持长驻资源。
 */
public class WebDavRemoteBackupStorage implements RemoteBackupStorage {

    private static final int TIMEOUT_MILLIS = 60_000;

    private final RemoteTarget target;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIMEOUT_MILLIS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    WebDavRemoteBackupStorage(RemoteTarget target) {
        this.target = target;
    }

    @Override
    public void test() {
        try {
            HttpResponse<byte[]> response = propfind(baseDirUrl(), "0");
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new BizException("WebDAV 认证失败，请检查账号与密码");
            }
            if (response.statusCode() == 404) {
                throw new BizException("WebDAV 远端目录不存在：" + target.getBasePath());
            }
            if (response.statusCode() != 207 && response.statusCode() != 200) {
                throw new BizException("WebDAV 探测失败，远端返回 " + response.statusCode());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 连接失败：" + e.getMessage());
        }
    }

    @Override
    public void put(String path, InputStream in, long size) {
        try {
            ensureRemoteDirs(path);
            HttpRequest request = builder(fileUrl(path), "PUT")
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Length", String.valueOf(size))
                    .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> in))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            requireSuccess(response, "上传备份");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 上传失败：" + e.getMessage());
        }
    }

    @Override
    public List<RemoteEntry> list(String prefix) {
        try {
            String dirPath = normalizeDir(target.getBasePath());
            HttpResponse<byte[]> response = propfind(baseDirUrl(), "1");
            requireSuccess(response, "列出备份");
            return parseListing(new String(response.body(), StandardCharsets.UTF_8), dirPath, prefix);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 列表失败：" + e.getMessage());
        }
    }

    @Override
    public void fetch(String path, Path destination) {
        try {
            HttpRequest request = builder(fileUrl(path), "GET").GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) {
                throw new BizException("远端备份文件不存在：" + path);
            }
            requireSuccess(response.statusCode(), "下载备份");
            try (InputStream body = response.body()) {
                Files.copy(body, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 下载失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String path) {
        try {
            HttpRequest request = builder(fileUrl(path), "DELETE").build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();
            if (code != 404 && (code < 200 || code >= 300)) {
                throw new BizException("WebDAV 删除失败，远端返回 " + code);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("WebDAV 删除失败：" + e.getMessage());
        }
    }

    private HttpRequest.Builder builder(String url, String method) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(TIMEOUT_MILLIS))
                .header("User-Agent", "YuDreamAdmin-Backup")
                .method(method, method.equals("GET") || method.equals("DELETE")
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.noBody());
        if (target.getUsername() != null && !target.getUsername().isBlank()) {
            String token = Base64.getEncoder().encodeToString(
                    (target.getUsername() + ":" + (target.getPassword() == null ? "" : target.getPassword()))
                            .getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        return builder;
    }

    private HttpResponse<byte[]> propfind(String url, String depth) throws Exception {
        HttpRequest request = builder(url, "PROPFIND")
                .header("Depth", depth)
                .header("Content-Type", "application/xml")
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                                + "<D:propfind xmlns:D=\"DAV:\"><D:prop><D:displayname/><D:getcontentlength/>"
                                + "<D:getlastmodified/><D:resourcetype/></D:prop></D:propfind>",
                        StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private List<RemoteEntry> parseListing(String xml, String dirPath, String prefix) {
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
            List<RemoteEntry> entries = new ArrayList<>();
            NodeList responses = root.getElementsByTagNameNS("*", "response");
            for (int i = 0; i < responses.getLength(); i++) {
                Element response = (Element) responses.item(i);
                String href = textOfFirstChild(response, "href");
                if (href == null) {
                    continue;
                }
                String decodedPath = decodedPath(href);
                if (decodedPath.endsWith("/")) {
                    continue;
                }
                String parent = decodedPath.substring(0, decodedPath.lastIndexOf('/') + 1);
                if (!parent.equals(dirPath)) {
                    continue;
                }
                String name = decodedPath.substring(decodedPath.lastIndexOf('/') + 1);
                if (prefix != null && !prefix.isBlank() && !name.startsWith(prefix)) {
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
                entries.add(new RemoteEntry(name, size, modified));
            }
            return entries;
        } catch (Exception e) {
            throw new BizException("解析 WebDAV 响应失败：" + e.getMessage());
        }
    }

    private void ensureRemoteDirs(String path) throws Exception {
        String dirPath = normalizeDir(target.getBasePath() + "/"
                + (path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ""));
        String[] segments = dirPath.split("/");
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            current.append('/').append(segment);
            HttpRequest request = builder(schemeHost() + encodePath(current.toString()), "MKCOL").build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();
            if (code != 201 && code != 405 && code != 301) {
                if (code == 401 || code == 403) {
                    throw new BizException("WebDAV 认证失败，无法创建目录：" + current);
                }
                // 其他错误（如父级已存在判断分歧）留给后续 PUT 校验
            }
        }
    }

    private void requireSuccess(HttpResponse<byte[]> response, String action) {
        requireSuccess(response.statusCode(), action);
    }

    private void requireSuccess(int code, String action) {
        if (code == 401 || code == 403) {
            throw new BizException(action + "失败：WebDAV 认证被拒绝");
        }
        if (code < 200 || code >= 300) {
            throw new BizException(action + "失败，远端返回 " + code);
        }
    }

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
            builder.append(java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
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
}
