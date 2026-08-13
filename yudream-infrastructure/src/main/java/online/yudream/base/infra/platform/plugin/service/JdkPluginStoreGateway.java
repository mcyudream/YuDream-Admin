package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.port.PluginStoreGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginCompatibility;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDependency;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDetail;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginJar;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginPublisher;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginSource;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginVersion;
import online.yudream.base.domain.platform.plugin.valobj.SemVer;
import online.yudream.base.domain.platform.plugin.valobj.SemVerRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class JdkPluginStoreGateway implements PluginStoreGateway {

    private static final URI DEFAULT_ROOT = URI.create("https://nexus.yudream.online/repository/plugin-store-releases/index.json");
    private static final String STORE_UNAVAILABLE = "插件商店数据不可用";
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern PUBLISHER_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SPDX_LICENSE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.+-]{0,63}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int MAX_PUBLISHER_NAME_LENGTH = 128;
    private static final int MAX_DISPLAY_URL_LENGTH = 2_048;
    private static final int MAX_RELEASE_NOTES_LENGTH = 4_096;

    private final PluginProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdkPluginStoreGateway(PluginProperties properties, ObjectMapper objectMapper) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.getStoreConnectTimeoutMillis())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), objectMapper);
    }

    public JdkPluginStoreGateway(PluginProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw unavailable();
        }
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PluginStorePluginInfo> list() {
        StoreLocation location = storeLocation();
        List<PluginStorePluginInfo> result = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode entry : rootEntries(readJson(location.rootUrl()))) {
            PluginStorePluginInfo info = listPlugin(location, entry);
            if (info != null && codes.add(info.getCode())) {
                result.add(info);
            }
        }
        return result;
    }

    private PluginStorePluginInfo listPlugin(StoreLocation location, JsonNode entry) {
        try {
            RootPlugin plugin = parseRootPlugin(entry, true);
            URI indexUrl = requireStoreReference(location, location.rootUrl(), plugin.index());
            List<IndexVersion> versions = indexVersions(readJson(indexUrl), plugin.code());
            IndexVersion lastVersion = versions.getLast();
            URI descriptorUrl = requireStoreReference(location, location.baseUrl(), lastVersion.descriptor());
            PluginStorePluginInfo info = new PluginStorePluginInfo();
            info.setCode(plugin.code());
            info.setDescriptor(descriptor(readJson(descriptorUrl), location, indexUrl, plugin.code(),
                    lastVersion.releaseVersion()));
            return info;
        } catch (BizException e) {
            return null;
        }
    }

    @Override
    public Optional<PluginStorePluginDetail> detail(String code) {
        if (!StringUtils.hasText(code) || !CODE.matcher(code).matches()) {
            throw unavailable();
        }
        StoreLocation location = storeLocation();
        RootPlugin target = null;
        Set<String> codes = new HashSet<>();
        for (JsonNode entry : rootEntries(readJson(location.rootUrl()))) {
            RootPlugin plugin = parseRootPlugin(entry, true);
            if (!codes.add(plugin.code())) {
                throw unavailable();
            }
            if (code.equals(plugin.code())) {
                target = plugin;
            }
        }
        if (target == null) {
            return Optional.empty();
        }
        URI indexUrl = requireStoreReference(location, location.rootUrl(), target.index());
        List<IndexVersion> indexVersions = indexVersions(readJson(indexUrl), code);
        List<PluginStorePluginVersion> versions = new ArrayList<>();
        for (IndexVersion version : indexVersions) {
            URI descriptorUrl = requireStoreReference(location, location.baseUrl(), version.descriptor());
            versions.add(new PluginStorePluginVersion(version.releaseVersion(),
                    descriptor(readJson(descriptorUrl), location, indexUrl, code, version.releaseVersion())));
        }
        return Optional.of(new PluginStorePluginDetail(code, List.copyOf(versions)));
    }

    @Override
    public void downloadJar(PluginStorePluginDescriptor descriptor, Path target) {
        if (descriptor == null || descriptor.jar() == null || target == null
                || !SHA_256.matcher(descriptor.jar().sha256() == null ? "" : descriptor.jar().sha256()).matches()) {
            throw unavailable();
        }
        boolean complete = false;
        try {
            URI uri = new URI(descriptor.jar().url());
            if (!isValidRootUri(uri)) {
                throw unavailable();
            }
            HttpRequest request = HttpRequest.newBuilder(uri.normalize())
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(1, properties.getStoreRequestTimeoutMillis())))
                    .header("Accept", "application/java-archive, application/octet-stream")
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw unavailable();
                }
                long maxBytes = Math.max(1, properties.getStoreMaxJarBytes());
                response.headers().firstValue("Content-Length").ifPresent(value -> {
                    if (parseContentLength(value) > maxBytes) {
                        throw unavailable();
                    }
                });
                writeJar(body, target, maxBytes, descriptor.jar().sha256());
                complete = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | URISyntaxException | RuntimeException e) {
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw unavailable();
        } finally {
            if (!complete) {
                deleteQuietly(target);
            }
        }
    }

    private void writeJar(InputStream input, Path target, long maxBytes, String expectedSha256) throws IOException {
        MessageDigest digest = sha256Digest();
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw unavailable();
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (!expectedSha256.equalsIgnoreCase(toHex(digest.digest()))) {
            throw unavailable();
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value));
        }
        return result.toString();
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private List<JsonNode> rootEntries(JsonNode root) {
        requireObject(root);
        requireSchemaVersion(root);
        JsonNode plugins = root.get("plugins");
        if (plugins == null || !plugins.isArray()) {
            throw unavailable();
        }
        List<JsonNode> entries = new ArrayList<>();
        plugins.forEach(entries::add);
        return entries;
    }

    private RootPlugin parseRootPlugin(JsonNode entry, boolean strict) {
        try {
            requireObject(entry);
            String code = requireText(entry, "code");
            String index = requireText(entry, "index");
            if (!CODE.matcher(code).matches()) {
                throw unavailable();
            }
            return new RootPlugin(code, index);
        } catch (BizException e) {
            if (strict) {
                throw e;
            }
            return null;
        }
    }

    private List<IndexVersion> indexVersions(JsonNode index, String expectedCode) {
        requireObject(index);
        requireSchemaVersion(index);
        if (!expectedCode.equals(requireText(index, "pluginCode"))) {
            throw unavailable();
        }
        JsonNode versions = index.get("versions");
        if (versions == null || !versions.isArray() || versions.isEmpty()) {
            throw unavailable();
        }
        List<IndexVersion> result = new ArrayList<>();
        Set<String> releaseVersions = new HashSet<>();
        for (JsonNode version : versions) {
            requireObject(version);
            String releaseVersion = requireSemanticVersion(version, "releaseVersion");
            if (!releaseVersions.add(releaseVersion)) {
                throw unavailable();
            }
            result.add(new IndexVersion(releaseVersion, requireText(version, "descriptor")));
        }
        return result;
    }

    private PluginStorePluginDescriptor descriptor(JsonNode descriptor, StoreLocation location, URI indexUrl,
                                                   String expectedCode, String expectedVersion) {
        requireObject(descriptor);
        requireSchemaVersion(descriptor);
        if (!expectedVersion.equals(requireSemanticVersion(descriptor, "releaseVersion"))) {
            throw unavailable();
        }
        JsonNode plugin = descriptor.get("plugin");
        requireObject(plugin);
        if (!expectedCode.equals(requireText(plugin, "code")) || !expectedVersion.equals(requireSemanticVersion(plugin, "version"))) {
            throw unavailable();
        }
        String main = requireText(plugin, "main");
        String displayName = optionalText(plugin, "displayName");
        String description = optionalText(plugin, "description");
        String icon = resolveOptionalReference(location, indexUrl, optionalText(plugin, "icon"));
        List<String> screenshots = resolveOptionalReferences(location, indexUrl, optionalTextArray(plugin, "screenshots"));
        PluginStorePluginPublisher publisher = parsePublisher(plugin.get("publisher"));
        PluginStorePluginSource source = parseSource(plugin.get("source"));
        String license = optionalLicense(plugin, "license");
        String releaseNotes = optionalReleaseNotes(plugin, "releaseNotes");
        PluginStorePluginCompatibility compatibility = parseCompatibility(plugin.get("compatibility"));
        List<PluginStorePluginDependency> dependencies = parseDependencies(plugin.get("dependencies"));

        JsonNode jar = descriptor.get("jar");
        requireObject(jar);
        String mavenCoordinates = requireText(jar, "mavenCoordinates");
        String jarUrl = resolveJarUrl(location, indexUrl, requireText(jar, "url"));
        String sha256 = requireText(jar, "sha256");
        if (!SHA_256.matcher(sha256).matches()) {
            throw unavailable();
        }
        return new PluginStorePluginDescriptor(expectedVersion, expectedCode, expectedVersion, main, displayName,
                description, icon, screenshots, publisher, source, license, releaseNotes, compatibility, dependencies,
                new PluginStorePluginJar(mavenCoordinates, jarUrl, sha256.toLowerCase(Locale.ROOT)));
    }

    private PluginStorePluginPublisher parsePublisher(JsonNode publisher) {
        if (publisher == null) {
            return null;
        }
        requireExactFields(publisher, Set.of("id", "name", "url", "verified"));
        String id = requireText(publisher, "id");
        String name = requireDisplayText(publisher, "name", MAX_PUBLISHER_NAME_LENGTH);
        if (!PUBLISHER_ID.matcher(id).matches()) {
            throw unavailable();
        }
        JsonNode verified = publisher.get("verified");
        if (verified == null || !verified.isBoolean()) {
            throw unavailable();
        }
        return new PluginStorePluginPublisher(id, name, requireDisplayUrl(publisher, "url"), verified.booleanValue());
    }

    private PluginStorePluginSource parseSource(JsonNode source) {
        if (source == null) {
            return null;
        }
        requireExactFields(source, Set.of("repository", "commit"));
        String commit = requireText(source, "commit");
        if (!COMMIT.matcher(commit).matches()) {
            throw unavailable();
        }
        return new PluginStorePluginSource(requireDisplayUrl(source, "repository"), commit);
    }

    private String optionalLicense(JsonNode plugin, String field) {
        String license = optionalText(plugin, field);
        if (license == null) {
            return null;
        }
        if (!SPDX_LICENSE.matcher(license).matches()) {
            throw unavailable();
        }
        return license;
    }

    private String optionalReleaseNotes(JsonNode plugin, String field) {
        JsonNode value = plugin.get(field);
        if (value == null) {
            return null;
        }
        return requireDisplayText(plugin, field, MAX_RELEASE_NOTES_LENGTH);
    }

    private String requireDisplayText(JsonNode node, String field, int maxLength) {
        String value = requireText(node, field);
        if (value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw unavailable();
        }
        return value;
    }

    private String requireDisplayUrl(JsonNode node, String field) {
        String value = requireText(node, field);
        if (value.length() > MAX_DISPLAY_URL_LENGTH) {
            throw unavailable();
        }
        try {
            URI uri = new URI(value);
            if (!isValidRootUri(uri)) {
                throw unavailable();
            }
            return uri.normalize().toString();
        } catch (URISyntaxException | RuntimeException e) {
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw unavailable();
        }
    }

    private PluginStorePluginCompatibility parseCompatibility(JsonNode compatibility) {
        if (compatibility == null) {
            return null;
        }
        requireAllowedFields(compatibility, Set.of("host", "spi", "frontendSdk"));
        return new PluginStorePluginCompatibility(
                optionalSemanticVersionRange(compatibility, "host"),
                optionalSemanticVersionRange(compatibility, "spi"),
                optionalSemanticVersionRange(compatibility, "frontendSdk"));
    }

    private List<PluginStorePluginDependency> parseDependencies(JsonNode dependencies) {
        if (dependencies == null) {
            return List.of();
        }
        if (!dependencies.isArray()) {
            throw unavailable();
        }
        List<PluginStorePluginDependency> result = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode dependency : dependencies) {
            requireExactFields(dependency, Set.of("code", "range", "required"));
            String code = requireText(dependency, "code");
            if (!CODE.matcher(code).matches() || !codes.add(code)) {
                throw unavailable();
            }
            String range = requireSemanticVersionRange(dependency, "range");
            JsonNode required = dependency.get("required");
            if (required == null || !required.isBoolean()) {
                throw unavailable();
            }
            result.add(new PluginStorePluginDependency(code, range, required.booleanValue()));
        }
        return List.copyOf(result);
    }

    private String resolveOptionalReference(StoreLocation location, URI baseUri, String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return requireStoreReference(location, baseUri, reference).toString();
    }

    private List<String> resolveOptionalReferences(StoreLocation location, URI baseUri, List<String> references) {
        List<String> result = new ArrayList<>();
        for (String reference : references) {
            if (!StringUtils.hasText(reference)) {
                throw unavailable();
            }
            result.add(requireStoreReference(location, baseUri, reference).toString());
        }
        return List.copyOf(result);
    }

    private String resolveJarUrl(StoreLocation location, URI baseUri, String value) {
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute()) {
                return requireStoreReference(location, baseUri, value).toString();
            }
            if (!isValidRootUri(uri)) {
                throw unavailable();
            }
            return uri.normalize().toString();
        } catch (URISyntaxException | RuntimeException e) {
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw unavailable();
        }
    }

    private JsonNode readJson(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(1, properties.getStoreRequestTimeoutMillis())))
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300 || !isJson(response)) {
                    throw unavailable();
                }
                long maxBytes = Math.max(1, properties.getStoreMaxResponseBytes());
                response.headers().firstValue("Content-Length").ifPresent(value -> {
                    if (parseContentLength(value) > maxBytes) {
                        throw unavailable();
                    }
                });
                JsonNode json = objectMapper.readTree(readLimited(body, maxBytes));
                if (json == null) {
                    throw unavailable();
                }
                return json;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | RuntimeException e) {
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw unavailable();
        }
    }

    private long parseContentLength(String value) {
        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            throw unavailable();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw unavailable();
        }
    }

    private StoreLocation storeLocation() {
        try {
            URI root = StringUtils.hasText(properties.getStoreRootUrl()) ? new URI(properties.getStoreRootUrl()) : DEFAULT_ROOT;
            if (!isValidRootUri(root) || containsTraversal(root.getRawPath())) {
                throw unavailable();
            }
            root = root.normalize();
            return new StoreLocation(root, root.resolve(".").normalize());
        } catch (URISyntaxException | RuntimeException e) {
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw unavailable();
        }
    }

    static boolean isValidRootUri(URI uri) {
        return uri != null && uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme())
                && StringUtils.hasText(uri.getHost()) && uri.getRawUserInfo() == null
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
    }

    static URI resolveRelativeStoreUri(URI rootUrl, URI baseUrl, String reference) {
        try {
            if (!isValidRootUri(rootUrl) || baseUrl == null || !StringUtils.hasText(reference)) {
                return null;
            }
            URI candidate = new URI(reference);
            String path = candidate.getRawPath();
            if (candidate.isAbsolute() || candidate.getRawAuthority() != null || (path != null && path.startsWith("/"))
                    || candidate.getRawQuery() != null || candidate.getRawFragment() != null || containsTraversal(path)) {
                return null;
            }
            URI resolved = baseUrl.resolve(candidate).normalize();
            URI storeBase = rootUrl.resolve(".").normalize();
            if (!isValidRootUri(resolved) || !sameOrigin(rootUrl, resolved)
                    || !isWithinBasePath(storeBase.getRawPath(), resolved.getRawPath())) {
                return null;
            }
            return resolved;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private URI resolveStoreReference(StoreLocation location, URI baseUri, String reference) {
        return resolveRelativeStoreUri(location.rootUrl(), baseUri, reference);
    }

    private URI requireStoreReference(StoreLocation location, URI baseUri, String reference) {
        URI resolved = resolveStoreReference(location, baseUri, reference);
        if (resolved == null) {
            throw unavailable();
        }
        return resolved;
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static boolean isWithinBasePath(String basePath, String resolvedPath) {
        String directory = basePath.endsWith("/") ? basePath : basePath + "/";
        return resolvedPath != null && resolvedPath.startsWith(directory);
    }

    private byte[] readLimited(InputStream input, long maxBytes) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw unavailable();
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean containsTraversal(String path) {
        if (path == null) {
            return false;
        }
        String decoded = path;
        for (int i = 0; i < 4; i++) {
            String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            if (next.equals(decoded)) {
                break;
            }
            decoded = next;
        }
        for (String segment : decoded.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isJson(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith("application/json"))
                .orElse(false);
    }

    private void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw unavailable();
        }
    }

    private void requireExactFields(JsonNode node, Set<String> requiredFields) {
        requireObject(node);
        Set<String> actualFields = new HashSet<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        if (!actualFields.equals(requiredFields)) {
            throw unavailable();
        }
    }

    private void requireAllowedFields(JsonNode node, Set<String> allowedFields) {
        requireObject(node);
        node.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw unavailable();
            }
        });
    }

    private void requireSchemaVersion(JsonNode node) {
        JsonNode value = node.get("schemaVersion");
        if (value == null || !value.isIntegralNumber() || value.intValue() != 1) {
            throw unavailable();
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.textValue())) {
            throw unavailable();
        }
        return value.textValue();
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value != null && !value.isTextual()) {
            throw unavailable();
        }
        return value == null ? null : value.textValue();
    }

    private String requireSemanticVersion(JsonNode node, String field) {
        String value = requireText(node, field);
        try {
            SemVer.parse(value);
            return value;
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private String requireSemanticVersionRange(JsonNode node, String field) {
        String value = requireText(node, field);
        try {
            SemVerRange.parse(value);
            return value;
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private String optionalSemanticVersionRange(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            return null;
        }
        return requireSemanticVersionRange(node, field);
    }

    private List<String> optionalTextArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            return List.of();
        }
        if (!value.isArray()) {
            throw unavailable();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw unavailable();
            }
            result.add(item.textValue());
        }
        return List.copyOf(result);
    }

    private static BizException unavailable() {
        return new BizException(STORE_UNAVAILABLE);
    }

    private record StoreLocation(URI rootUrl, URI baseUrl) {
    }

    private record RootPlugin(String code, String index) {
    }

    private record IndexVersion(String releaseVersion, String descriptor) {
    }
}
