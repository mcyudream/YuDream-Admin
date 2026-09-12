package online.yudream.base.application.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.assembler.PluginMarketPublicationAssembler;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationReviewCmd;
import online.yudream.base.application.platform.plugin.dto.PluginMarketPublicationDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationChannel;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketPublicationRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.SemVer;
import online.yudream.base.domain.platform.plugin.valobj.SemVerRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 自托管市场源发布服务：界面/API Key 双通道上传 → 解析 plugin.yml → 生成契约 descriptor →
 * 审核（可配置）→ 对外只读目录。{code}@{pluginVersion} 不可覆盖。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PluginMarketPublicationAppService {

    /** 发布物 JAR 在市场目录内的固定相对布局（descriptor 的相对引用与之对齐）。 */
    public static final String JAR_FILE_NAME = "plugin.jar";

    private static final String CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
    private static final String PUBLISHER_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
    private static final String LICENSE_PATTERN = "[A-Za-z0-9][A-Za-z0-9.+-]{0,63}";
    private static final int MAX_DISPLAY_TEXT_LENGTH = 512;
    private static final int MAX_RELEASE_NOTES_LENGTH = 4_096;

    private final PluginMarketPublicationRepo publicationRepo;
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final CapabilityAppService capabilityAppService;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectMapper objectMapper;

    @Value("${yudream.platform.plugin.market-source.directory:market-source}")
    private String marketDirectory;

    @Value("${yudream.platform.plugin.store-max-jar-bytes:104857600}")
    private long maxJarBytes;

    // ---------- 发布 ----------

    @Transactional
    public PluginMarketPublicationDTO publish(InputStream jarStream, long size, String releaseNotes,
                                              String metadataJson, Long publisherUserId, boolean pipeline) {
        capabilityAppService.ensureEnabled(PluginMarketSourceAppService.CAPABILITY_CODE,
                PluginMarketSourceAppService.CAPABILITY_NAME);
        if (size <= 0) {
            throw new BizException("插件 JAR 不能为空");
        }
        if (size > maxJarBytes) {
            throw new BizException("插件 JAR 超过大小限制");
        }
        Path directory = marketDirectory();
        Path staged = stageUpload(jarStream, directory);
        try {
            PluginDescriptorInfo descriptor = pluginRuntimeGateway.describe(staged)
                    .orElseThrow(() -> new BizException("上传文件不是有效的 YuDream 插件 JAR"));
            String code = validateCode(descriptor.code());
            String pluginVersion = validateVersion(descriptor.version());
            if (publicationRepo.findByCodeAndVersion(code, pluginVersion).isPresent()) {
                throw new BizException("版本已存在：" + code + "@" + pluginVersion + "（已发布版本不可覆盖，请发布新版本）");
            }
            String sha256 = sha256(staged);
            Map<String, Object> metadata = parseMetadata(metadataJson);
            String descriptorJson = buildDescriptorJson(descriptor, code, pluginVersion, releaseNotes, metadata, sha256);

            Path target = directory.resolve(code).resolve(pluginVersion).resolve(JAR_FILE_NAME);
            try {
                Files.createDirectories(target.getParent());
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new BizException("插件 JAR 保存失败：" + e.getMessage());
            }
            try {
                PluginMarketPublication publication = publicationRepo.save(PluginMarketPublication.builder()
                        .code(code)
                        .pluginVersion(pluginVersion)
                        .displayName(displayName(descriptor, code))
                        .description(sanitizeDisplayText(descriptor.description(), MAX_DISPLAY_TEXT_LENGTH))
                        .mainClass(descriptor.mainClass())
                        .dependencies(descriptor.dependencies())
                        .softDependencies(descriptor.softDependencies())
                        .releaseNotes(sanitizeReleaseNotes(releaseNotes))
                        .license(license(metadata))
                        .descriptorJson(descriptorJson)
                        .jarPath(directory.relativize(target).toString().replace('\\', '/'))
                        .sha256(sha256)
                        .sizeBytes(size)
                        .publisherUserId(publisherUserId)
                        .channel(pipeline ? PluginPublicationChannel.PIPELINE : PluginPublicationChannel.UI)
                        .status(reviewRequired() ? PluginPublicationStatus.PENDING : PluginPublicationStatus.PUBLISHED)
                        .build());
                log.info("插件市场发布物已提交：{}@{} 通道={} 状态={}", code, pluginVersion,
                        publication.getChannel(), publication.getStatus());
                return PluginMarketPublicationAssembler.toDTO(publication);
            } catch (RuntimeException e) {
                deleteQuietly(target);
                throw e;
            }
        } finally {
            deleteQuietly(staged);
        }
    }

    // ---------- 管理与审核 ----------

    @Transactional(readOnly = true)
    public List<PluginMarketPublicationDTO> list(String status) {
        capabilityAppService.ensureEnabled(PluginMarketSourceAppService.CAPABILITY_CODE,
                PluginMarketSourceAppService.CAPABILITY_NAME);
        List<PluginMarketPublication> publications = StringUtils.hasText(status)
                ? publicationRepo.findByStatus(parseStatus(status))
                : publicationRepo.findAll();
        return PluginMarketPublicationAssembler.toDTOList(publications);
    }

    @Transactional
    public PluginMarketPublicationDTO accept(PluginMarketPublicationReviewCmd cmd, Long reviewerId) {
        return review(cmd, reviewerId, (publication, note) -> publication.accept(reviewerId, note));
    }

    @Transactional
    public PluginMarketPublicationDTO reject(PluginMarketPublicationReviewCmd cmd, Long reviewerId) {
        return review(cmd, reviewerId, (publication, note) -> publication.reject(reviewerId, note));
    }

    @Transactional
    public PluginMarketPublicationDTO unpublish(PluginMarketPublicationReviewCmd cmd, Long reviewerId) {
        return review(cmd, reviewerId, (publication, note) -> publication.revoke(reviewerId, note));
    }

    private PluginMarketPublicationDTO review(PluginMarketPublicationReviewCmd cmd, Long reviewerId,
                                              java.util.function.BiConsumer<PluginMarketPublication, String> action) {
        capabilityAppService.ensureEnabled(PluginMarketSourceAppService.CAPABILITY_CODE,
                PluginMarketSourceAppService.CAPABILITY_NAME);
        if (cmd == null || cmd.getId() == null) {
            throw new BizException("发布物不存在");
        }
        PluginMarketPublication publication = publicationRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("发布物不存在"));
        action.accept(publication, StringUtils.hasText(cmd.getNote()) ? cmd.getNote().trim() : null);
        PluginMarketPublication saved = publicationRepo.save(publication);
        log.info("插件市场发布物 {}@{} 审核动作完成：{}（审核人 {}）", saved.getCode(), saved.getPluginVersion(),
                saved.getStatus(), reviewerId);
        return PluginMarketPublicationAssembler.toDTO(saved);
    }

    /** 审核开关：能力配置 reviewRequired，未配置或值非 false 时默认需要审核。 */
    public boolean reviewRequired() {
        return capabilityModuleRepo.findByCode(PluginMarketSourceAppService.CAPABILITY_CODE)
                .map(this::reviewRequiredOf)
                .orElse(true);
    }

    @Transactional
    public boolean updateReviewRequired(boolean required) {
        capabilityAppService.ensureEnabled(PluginMarketSourceAppService.CAPABILITY_CODE,
                PluginMarketSourceAppService.CAPABILITY_NAME);
        CapabilityModule module = capabilityModuleRepo.findByCode(PluginMarketSourceAppService.CAPABILITY_CODE)
                .orElseThrow(() -> new BizException("插件市场源能力未初始化，请先在平台能力中启用"));
        Map<String, String> config = new LinkedHashMap<>(module.getConfig() == null ? Map.of() : module.getConfig());
        config.put("reviewRequired", Boolean.toString(required));
        module.updateConfig(config);
        capabilityModuleRepo.save(module);
        return required;
    }

    // ---------- 公开契约（匿名，消费端网关按 schemaVersion=1 拉取） ----------

    public String rootIndexJson() {
        requirePubliclyServed();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode plugins = root.putArray("plugins");
        for (String code : new TreeSet<>(publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)
                .stream().map(PluginMarketPublication::getCode).toList())) {
            ObjectNode plugin = plugins.addObject();
            plugin.put("code", code);
            plugin.put("index", code + "/index.json");
        }
        return toJson(root);
    }

    public Optional<String> codeIndexJson(String code) {
        requirePubliclyServed();
        if (!StringUtils.hasText(code) || !code.matches(CODE_PATTERN)) {
            return Optional.empty();
        }
        List<PluginMarketPublication> versions = publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)
                .stream().filter(item -> code.equals(item.getCode()))
                // 消费端网关取 versions 数组最后一项为最新版，必须按 SemVer 升序输出
                .sorted(Comparator.comparing(item -> SemVer.parse(item.getPluginVersion())))
                .toList();
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("pluginCode", code);
        ArrayNode nodes = root.putArray("versions");
        for (PluginMarketPublication publication : versions) {
            ObjectNode version = nodes.addObject();
            version.put("releaseVersion", publication.getPluginVersion());
            version.put("descriptor", "versions/" + publication.getPluginVersion() + "/descriptor.json");
        }
        return Optional.of(toJson(root));
    }

    /** 已发布版本的 descriptor 原文（发布时生成并经契约校验）。 */
    public Optional<String> publishedDescriptorJson(String code, String pluginVersion) {
        requirePubliclyServed();
        return publishedPublication(code, pluginVersion).map(PluginMarketPublication::getDescriptorJson);
    }

    /** 已发布版本的 JAR 文件；文件缺失视为内容不可用。 */
    public Optional<Path> publishedJarFile(String code, String pluginVersion) {
        requirePubliclyServed();
        return publishedPublication(code, pluginVersion)
                .map(publication -> marketDirectory().resolve(publication.getJarPath()))
                .filter(Files::isRegularFile);
    }

    // ---------- 内部实现 ----------

    private Optional<PluginMarketPublication> publishedPublication(String code, String pluginVersion) {
        if (!StringUtils.hasText(code) || !code.matches(CODE_PATTERN)
                || !StringUtils.hasText(pluginVersion) || !pluginVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            return Optional.empty();
        }
        return publicationRepo.findByCodeAndVersion(code, pluginVersion)
                .filter(PluginMarketPublication::published);
    }

    private void requirePubliclyServed() {
        capabilityAppService.ensureEnabled(PluginMarketSourceAppService.CAPABILITY_CODE,
                PluginMarketSourceAppService.CAPABILITY_NAME);
    }

    private boolean reviewRequiredOf(CapabilityModule module) {
        String value = module.getConfig() == null ? null : module.getConfig().get("reviewRequired");
        // defaultConfig 只播种空配置行：读取处运行时回落默认值（默认需要审核）
        return !"false".equalsIgnoreCase(value);
    }

    private Path marketDirectory() {
        try {
            Path directory = Path.of(marketDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            return directory;
        } catch (IOException e) {
            throw new BizException("市场源存储目录不可用：" + e.getMessage());
        }
    }

    private Path stageUpload(InputStream jarStream, Path directory) {
        Path staged = null;
        try {
            staged = Files.createTempFile(directory, ".market-publish-", ".tmp");
            try (jarStream) {
                Files.copy(jarStream, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            return staged;
        } catch (IOException e) {
            deleteQuietly(staged);
            throw new BizException("插件 JAR 保存失败：" + e.getMessage());
        }
    }

    private String validateCode(String code) {
        if (!StringUtils.hasText(code) || !code.matches(CODE_PATTERN)) {
            throw new BizException("插件 JAR 的 plugin.yml name 不符合市场源契约（字母开头，仅字母数字点下划线连字符）");
        }
        return code;
    }

    private String validateVersion(String version) {
        if (!StringUtils.hasText(version)) {
            throw new BizException("插件 JAR 缺少版本号");
        }
        try {
            SemVer.parse(version);
        } catch (IllegalArgumentException e) {
            throw new BizException("插件版本必须是严格 SemVer（x.y.z，无预发布后缀）：" + version);
        }
        return version;
    }

    private String displayName(PluginDescriptorInfo descriptor, String code) {
        String displayName = descriptor.name();
        return StringUtils.hasText(displayName) && !displayName.equals(code)
                ? sanitizeDisplayText(displayName, MAX_DISPLAY_TEXT_LENGTH)
                : null;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, objectMapper.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (IOException e) {
            throw new BizException("metadata 不是合法的 JSON：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String buildDescriptorJson(PluginDescriptorInfo descriptor, String code, String pluginVersion,
                                       String releaseNotes, Map<String, Object> metadata, String sha256) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("releaseVersion", pluginVersion);
            ObjectNode plugin = root.putObject("plugin");
            plugin.put("code", code);
            plugin.put("version", pluginVersion);
            plugin.put("main", descriptor.mainClass());
            String displayName = displayName(descriptor, code);
            if (displayName != null) {
                plugin.put("displayName", displayName);
            }
            String description = sanitizeDisplayText(descriptor.description(), MAX_DISPLAY_TEXT_LENGTH);
            if (description != null) {
                plugin.put("description", description);
            }
            String icon = descriptor.icon();
            // 消费端网关只接受 iconify 形态的 icon 内联值，其余（相对路径等）不下发
            if (icon != null && (icon.startsWith("i-") || icon.matches("^[A-Za-z0-9_-]+:[A-Za-z0-9_-]+$"))) {
                plugin.put("icon", icon);
            }
            ObjectNode publisher = publisherNode(metadata);
            if (publisher != null) {
                plugin.set("publisher", publisher);
            }
            String license = license(metadata);
            if (license != null) {
                plugin.put("license", license);
            }
            String notes = sanitizeReleaseNotes(releaseNotes);
            if (notes != null) {
                plugin.put("releaseNotes", notes);
            }
            ObjectNode compatibility = compatibilityNode(metadata);
            if (compatibility != null) {
                plugin.set("compatibility", compatibility);
            }
            ArrayNode dependencies = plugin.putArray("dependencies");
            TreeSet<String> seen = new TreeSet<>();
            for (String dependency : descriptor.dependencies()) {
                if (StringUtils.hasText(dependency) && seen.add(dependency.trim())) {
                    ObjectNode node = dependencies.addObject();
                    node.put("code", dependency.trim());
                    node.put("range", "x");
                    node.put("required", true);
                }
            }
            for (String dependency : descriptor.softDependencies()) {
                if (StringUtils.hasText(dependency) && seen.add(dependency.trim())) {
                    ObjectNode node = dependencies.addObject();
                    node.put("code", dependency.trim());
                    node.put("range", "x");
                    node.put("required", false);
                }
            }
            ObjectNode jar = root.putObject("jar");
            jar.put("mavenCoordinates", "self-hosted:" + code + ":" + pluginVersion);
            jar.put("url", "versions/" + pluginVersion + "/" + JAR_FILE_NAME);
            jar.put("sha256", sha256);
            return objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new BizException("生成市场 descriptor 失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ObjectNode publisherNode(Map<String, Object> metadata) {
        Object raw = metadata.get("publisher");
        if (!(raw instanceof Map<?, ?> publisher)) {
            return null;
        }
        String id = text(publisher.get("id"));
        String name = text(publisher.get("name"));
        String url = text(publisher.get("url"));
        Object verified = publisher.get("verified");
        if (id == null && name == null && url == null && verified == null) {
            return null;
        }
        if (id == null || !id.matches(PUBLISHER_ID_PATTERN)) {
            throw new BizException("metadata.publisher.id 必须是 64 位内的字母数字标识");
        }
        String sanitizedName = sanitizeDisplayText(name, 128);
        if (sanitizedName == null) {
            throw new BizException("metadata.publisher.name 不能为空且不能包含控制字符");
        }
        if (url == null || !isValidHttpsUrl(url)) {
            throw new BizException("metadata.publisher.url 必须是 HTTPS 地址");
        }
        if (!(verified instanceof Boolean verifiedFlag)) {
            throw new BizException("metadata.publisher.verified 必须是布尔值");
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("name", sanitizedName);
        node.put("url", url);
        node.put("verified", verifiedFlag);
        return node;
    }

    private String license(Map<String, Object> metadata) {
        String license = text(metadata.get("license"));
        if (license == null) {
            return null;
        }
        if (!license.matches(LICENSE_PATTERN)) {
            throw new BizException("metadata.license 必须是 SPDX 表达式（如 MIT、Apache-2.0）");
        }
        return license;
    }

    private ObjectNode compatibilityNode(Map<String, Object> metadata) {
        Object raw = metadata.get("compatibility");
        if (!(raw instanceof Map<?, ?> compatibility)) {
            return null;
        }
        ObjectNode node = objectMapper.createObjectNode();
        boolean any = false;
        for (String key : List.of("host", "spi", "frontendSdk")) {
            String range = text(compatibility.get(key));
            if (range == null) {
                continue;
            }
            try {
                SemVerRange.parse(range);
            } catch (IllegalArgumentException e) {
                throw new BizException("metadata.compatibility." + key + " 不是合法的 SemVer 区间：" + range);
            }
            node.put(key, range);
            any = true;
        }
        return any ? node : null;
    }

    private String text(Object value) {
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            return null;
        }
        return stringValue.trim();
    }

    private String sanitizeDisplayText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value.trim();
        if (sanitized.length() > maxLength || sanitized.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        return sanitized;
    }

    /** 消费端契约禁止控制字符，发布说明压成单行。 */
    private String sanitizeReleaseNotes(String releaseNotes) {
        if (!StringUtils.hasText(releaseNotes)) {
            return null;
        }
        String sanitized = releaseNotes.replaceAll("\\s*\\r?\\n\\s*", " ").trim();
        if (sanitized.length() > MAX_RELEASE_NOTES_LENGTH || sanitized.chars().anyMatch(Character::isISOControl)) {
            throw new BizException("发布说明过长（> " + MAX_RELEASE_NOTES_LENGTH + "）或包含控制字符");
        }
        return sanitized;
    }

    private boolean isValidHttpsUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            return uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme())
                    && StringUtils.hasText(uri.getHost()) && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private PluginPublicationStatus parseStatus(String status) {
        try {
            return PluginPublicationStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new BizException("未知的发布物状态：" + status);
        }
    }

    private String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BizException("计算插件 JAR 校验和失败：" + e.getMessage());
        }
    }

    private String toJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new BizException("生成市场目录失败：" + e.getMessage());
        }
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
}
