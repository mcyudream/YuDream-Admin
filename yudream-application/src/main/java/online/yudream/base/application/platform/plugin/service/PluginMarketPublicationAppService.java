package online.yudream.base.application.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.assembler.PluginMarketPublicationAssembler;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationEditCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationReviewCmd;
import online.yudream.base.application.platform.plugin.dto.PluginMarketPublicationDTO;
import online.yudream.base.application.system.setting.service.SettingAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationChannel;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketPublicationRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMarketCategories;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 自托管市场源发布服务：界面/API Key 双通道上传 → 解析 plugin.yml → 生成契约 descriptor →
 * 审核（可配置）→ v2 交互式目录对外服务。{code}@{pluginVersion} 不可覆盖。
 * 分类/标签是社区元数据（plugin.yml 不携带），发布表单或 metadata 提供，编辑可改。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PluginMarketPublicationAppService {

    /** 发布物 JAR 在市场目录内的固定相对布局（descriptor 的相对引用与之对齐）。 */
    public static final String JAR_FILE_NAME = "plugin.jar";
    public static final String V2_PROTOCOL = "yudream-market-v2";

    private static final String CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
    private static final String PUBLISHER_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
    private static final String LICENSE_PATTERN = "[A-Za-z0-9][A-Za-z0-9.+-]{0,63}";
    private static final int MAX_DISPLAY_TEXT_LENGTH = 512;
    private static final int MAX_RELEASE_NOTES_LENGTH = 4_096;
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 24;
    private static final int MAX_PAGE_SIZE = 100;

    private final PluginMarketPublicationRepo publicationRepo;
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final CapabilityAppService capabilityAppService;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final PluginUserCatalogAppService pluginUserCatalogAppService;
    private final SettingAppService settingAppService;
    private final ObjectMapper objectMapper;

    @Value("${yudream.platform.plugin.market-source.directory:market-source}")
    private String marketDirectory;

    @Value("${yudream.platform.plugin.store-max-jar-bytes:104857600}")
    private long maxJarBytes;

    // ---------- 发布 ----------

    @Transactional
    public PluginMarketPublicationDTO publish(InputStream jarStream, long size, String releaseNotes,
                                              String metadataJson, String category, List<String> tags,
                                              Long publisherUserId, boolean pipeline) {
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
            String effectiveCategory = firstHasText(category, text(metadata.get("category")));
            List<String> effectiveTags = tags != null && !tags.isEmpty() ? tags : tagsFromMetadata(metadata);
            String normalizedCategory = normalizeCategory(effectiveCategory);
            List<String> normalizedTags = normalizeTags(effectiveTags);
            Map<String, Object> compatibility = compatibilityMap(metadata);
            Map<String, Object> publisher = publisherMap(metadata);
            String descriptorJson = buildDescriptorJson(code, pluginVersion, descriptor.mainClass(),
                    displayName(descriptor, code), sanitizeDisplayText(descriptor.description(), MAX_DISPLAY_TEXT_LENGTH),
                    descriptor.icon(), descriptor.dependencies(), descriptor.softDependencies(),
                    releaseNotes, license(metadata), compatibility, publisher, sha256);

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
                        .icon(descriptor.icon())
                        .releaseNotes(sanitizeReleaseNotes(releaseNotes))
                        .license(license(metadata))
                        .category(normalizedCategory)
                        .tags(normalizedTags)
                        .compatibilityJson(toJsonString(compatibility))
                        .publisherJson(toJsonString(publisher))
                        .descriptorJson(descriptorJson)
                        .jarPath(directory.relativize(target).toString().replace('\\', '/'))
                        .sha256(sha256)
                        .sizeBytes(size)
                        .downloadCount(0L)
                        .publisherUserId(publisherUserId)
                        .channel(pipeline ? PluginPublicationChannel.PIPELINE : PluginPublicationChannel.UI)
                        .status(reviewRequired() ? PluginPublicationStatus.PENDING : PluginPublicationStatus.PUBLISHED)
                        .build());
                log.info("插件市场发布物已提交：{}@{} 通道={} 状态={}", code, pluginVersion,
                        publication.getChannel(), publication.getStatus());
                return toDTO(publication);
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
        return publications.stream().map(this::toDTO).toList();
    }

    /** 编辑展示元数据与分类标签；不重置审核状态，重生成 descriptor。 */
    @Transactional
    public PluginMarketPublicationDTO edit(PluginMarketPublicationEditCmd cmd) {
        capabilityAppService.ensureEnabled(PluginMarketSourceAppService.CAPABILITY_CODE,
                PluginMarketSourceAppService.CAPABILITY_NAME);
        if (cmd == null || cmd.getId() == null) {
            throw new BizException("发布物不存在");
        }
        PluginMarketPublication publication = publicationRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("发布物不存在"));
        String displayName = cmd.getDisplayName() == null ? null
                : sanitizeDisplayText(cmd.getDisplayName(), MAX_DISPLAY_TEXT_LENGTH);
        String description = cmd.getDescription() == null ? null
                : sanitizeDisplayText(cmd.getDescription(), MAX_DISPLAY_TEXT_LENGTH);
        String releaseNotes = cmd.getReleaseNotes() == null ? null : sanitizeReleaseNotes(cmd.getReleaseNotes());
        String license = cmd.getLicense() == null ? null : validateLicense(cmd.getLicense());
        String category = cmd.getCategory() == null ? null : normalizeCategory(cmd.getCategory());
        List<String> tags = cmd.getTags() == null ? null : normalizeTags(cmd.getTags());
        String compatibilityJson = cmd.getCompatibility() == null ? null : toJsonString(cmd.getCompatibility());
        publication.updateDisplayInfo(displayName, description, releaseNotes, license, category, tags, compatibilityJson);
        String descriptorJson = buildDescriptorJson(publication.getCode(), publication.getPluginVersion(),
                publication.getMainClass(), publication.getDisplayName(), publication.getDescription(),
                publication.getIcon(), publication.getDependencies(), publication.getSoftDependencies(),
                publication.getReleaseNotes(), publication.getLicense(),
                readMap(publication.getCompatibilityJson()), readMap(publication.getPublisherJson()),
                publication.getSha256());
        publication.setDescriptorJson(descriptorJson);
        return toDTO(publicationRepo.save(publication));
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

    /** 硬删除：移除发布记录与 JAR 文件（区别于下架的保留备查）。 */
    @Transactional
    public void delete(Long id) {
        capabilityAppService.ensureEnabled(PluginMarketSourceAppService.CAPABILITY_CODE,
                PluginMarketSourceAppService.CAPABILITY_NAME);
        PluginMarketPublication publication = publicationRepo.findById(id)
                .orElseThrow(() -> new BizException("发布物不存在"));
        publicationRepo.deleteById(publication.getId());
        deleteQuietly(jarFile(publication));
        log.info("插件市场发布物已删除：{}@{}", publication.getCode(), publication.getPluginVersion());
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
        return toDTO(saved);
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

    // ---------- v2 交互式目录（裸 JSON 协议，匿名可读） ----------

    public String manifestJson() {
        requirePubliclyServed();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("protocol", V2_PROTOCOL);
        root.put("name", siteName());
        root.put("pluginCount", groupSummaries().size());
        return toJson(root);
    }

    public String categoriesJson() {
        requirePubliclyServed();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PluginSummary summary : groupSummaries()) {
            if (StringUtils.hasText(summary.category())) {
                counts.merge(summary.category(), 1L, Long::sum);
            }
        }
        ArrayNode items = objectMapper.createArrayNode();
        for (String name : PluginMarketCategories.ALL) {
            ObjectNode node = items.addObject();
            node.put("code", name);
            node.put("name", name);
            node.put("count", counts.getOrDefault(name, 0L));
        }
        return toJson(items);
    }

    public String tagsJson(int limit) {
        requirePubliclyServed();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PluginSummary summary : groupSummaries()) {
            for (String tag : summary.tags()) {
                counts.merge(tag, 1L, Long::sum);
            }
        }
        ArrayNode items = objectMapper.createArrayNode();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(1, Math.min(limit, 100)))
                .forEach(entry -> {
                    ObjectNode node = items.addObject();
                    node.put("tag", entry.getKey());
                    node.put("count", entry.getValue());
                });
        return toJson(items);
    }

    public String pagePluginsJson(String search, String categories, String tags, Long authorId,
                                  String publishedAfter, String publishedBefore, String sort, int page, int size) {
        requirePubliclyServed();
        List<PluginSummary> items = filteredSummaries(search, categories, tags, authorId,
                parseTime(publishedAfter, "publishedAfter"), parseTime(publishedBefore, "publishedBefore"));
        Comparator<PluginSummary> comparator = switch (StringUtils.hasText(sort) ? sort : "newest") {
            case "downloads" -> Comparator.comparingLong(PluginSummary::downloads).reversed();
            case "updated" -> Comparator.comparing(PluginSummary::updatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "name" -> Comparator.comparing(PluginSummary::code);
            case "newest" -> Comparator.comparing(PluginSummary::publishedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> throw new BizException("未知的排序方式：" + sort);
        };
        items = items.stream().sorted(comparator).toList();
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int from = (safePage - 1) * safeSize;
        int to = Math.min(items.size(), from + safeSize);

        Map<Long, String> authorNames = resolveAuthorNames(items.subList(from, to));
        ObjectNode root = objectMapper.createObjectNode();
        root.put("total", items.size());
        root.put("page", safePage);
        root.put("size", safeSize);
        ArrayNode nodes = root.putArray("items");
        for (PluginSummary summary : items.subList(from, to)) {
            nodes.add(summaryNode(summary, authorNames.get(summary.authorId())));
        }
        return toJson(root);
    }

    public Optional<String> pluginDetailJson(String code) {
        requirePubliclyServed();
        if (!StringUtils.hasText(code) || !code.matches(CODE_PATTERN)) {
            return Optional.empty();
        }
        List<PluginMarketPublication> versions = publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)
                .stream().filter(item -> code.equals(item.getCode()))
                .sorted(Comparator.comparing(item -> SemVer.parse(item.getPluginVersion())))
                .toList();
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        PluginSummary summary = PluginSummary.of(code, versions);
        Map<Long, String> authorNames = resolveAuthorNames(List.of(summary));
        ObjectNode root = summaryNode(summary, authorNames.get(summary.authorId()));
        ArrayNode versionNodes = root.putArray("versions");
        for (PluginMarketPublication publication : versions) {
            ObjectNode node = versionNodes.addObject();
            node.put("version", publication.getPluginVersion());
            node.put("releaseNotes", publication.getReleaseNotes());
            node.put("license", publication.getLicense());
            node.put("sha256", publication.getSha256());
            node.put("sizeBytes", publication.getSizeBytes());
            node.put("downloads", publication.getDownloadCount() == null ? 0L : publication.getDownloadCount());
            node.put("publishedAt", publication.getCreateTime() == null ? null : publication.getCreateTime().toString());
            node.put("downloadPath", "plugins/" + code + "/versions/" + publication.getPluginVersion() + "/download");
            ArrayNode dependencies = node.putArray("dependencies");
            appendDependency(dependencies, publication.getDependencies(), true);
            appendDependency(dependencies, publication.getSoftDependencies(), false);
            ObjectNode compatibility = compatibilityNode(readMap(publication.getCompatibilityJson()));
            if (compatibility != null) {
                node.set("compatibility", compatibility);
            }
        }
        return Optional.of(toJson(root));
    }

    /** 解析已发布版本的 JAR 文件并递增下载计数。 */
    public Optional<Path> downloadPublication(String code, String pluginVersion) {
        requirePubliclyServed();
        Optional<PluginMarketPublication> publication = publishedPublication(code, pluginVersion);
        publication.ifPresent(item -> publicationRepo.incrementDownloadCount(item.getId()));
        return publication.map(this::jarFile);
    }

    /** legacy 静态端点的下载同样计数。 */
    public Optional<Path> incrementDownloadAndResolveLegacyJar(String code, String pluginVersion) {
        return downloadPublication(code, pluginVersion);
    }

    // ---------- legacy 静态契约（schemaVersion=1，保留兼容旧消费端） ----------

    public String rootIndexJson() {
        requirePubliclyServed();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode plugins = root.putArray("plugins");
        for (String code : new TreeSet<>(groupSummaries().stream().map(PluginSummary::code).toList())) {
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

    /** 已发布版本的 descriptor 原文（发布/编辑时生成并经契约校验）。 */
    public Optional<String> publishedDescriptorJson(String code, String pluginVersion) {
        requirePubliclyServed();
        return publishedPublication(code, pluginVersion).map(PluginMarketPublication::getDescriptorJson);
    }

    // ---------- 汇总与过滤 ----------

    private List<PluginSummary> filteredSummaries(String search, String categories, String tags, Long authorId,
                                                  LocalDateTime after, LocalDateTime before) {
        Set<String> categorySet = splitCsvExact(categories);
        Set<String> tagSet = splitCsv(tags);
        String keyword = search == null ? "" : search.trim().toLowerCase();
        return groupSummaries().stream()
                .filter(summary -> categorySet.isEmpty() || (summary.category() != null && categorySet.contains(summary.category())))
                .filter(summary -> tagSet.isEmpty() || summary.tags().stream().anyMatch(tagSet::contains))
                .filter(summary -> authorId == null || authorId.equals(summary.authorId()))
                .filter(summary -> after == null || summary.updatedAt().isAfter(after))
                .filter(summary -> before == null || summary.updatedAt().isBefore(before))
                .filter(summary -> !StringUtils.hasText(keyword)
                        || summary.code().toLowerCase().contains(keyword)
                        || containsIgnoreCase(summary.displayName(), keyword)
                        || containsIgnoreCase(summary.description(), keyword))
                .toList();
    }

    /** PUBLISHED 发布物按 code 分组：最新版取 SemVer 最大，下载量求和，时间为首末发布时间。 */
    private List<PluginSummary> groupSummaries() {
        Map<String, List<PluginMarketPublication>> grouped = new LinkedHashMap<>();
        for (PluginMarketPublication publication : publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)) {
            grouped.computeIfAbsent(publication.getCode(), key -> new ArrayList<>()).add(publication);
        }
        return grouped.entrySet().stream()
                .map(entry -> PluginSummary.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<Long, String> resolveAuthorNames(List<PluginSummary> summaries) {
        List<String> authorIds = summaries.stream()
                .map(PluginSummary::authorId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .map(String::valueOf)
                .toList();
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (var user : pluginUserCatalogAppService.resolveUsers(authorIds)) {
            try {
                names.put(Long.valueOf(user.getId()), user.getNickname());
            } catch (NumberFormatException ignored) {
                // 非数字 id 不参与解析
            }
        }
        return names;
    }

    private ObjectNode summaryNode(PluginSummary summary, String authorName) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("code", summary.code());
        node.put("displayName", summary.displayName());
        node.put("description", summary.description());
        node.put("icon", summary.icon());
        node.put("category", summary.category());
        ArrayNode tags = node.putArray("tags");
        summary.tags().forEach(tags::add);
        node.put("authorId", summary.authorId() == null ? null : summary.authorId().toString());
        node.put("authorName", authorName);
        node.put("latestVersion", summary.latestVersion());
        node.put("downloads", summary.downloads());
        node.put("publishedAt", summary.publishedAt() == null ? null : summary.publishedAt().toString());
        node.put("updatedAt", summary.updatedAt() == null ? null : summary.updatedAt().toString());
        node.put("license", summary.license());
        return node;
    }

    private record PluginSummary(String code, String displayName, String description, String icon,
                                 String category, List<String> tags, String license, Long authorId,
                                 String latestVersion, long downloads, LocalDateTime publishedAt, LocalDateTime updatedAt) {

        static PluginSummary of(String code, List<PluginMarketPublication> versions) {
            List<PluginMarketPublication> sorted = versions.stream()
                    .sorted(Comparator.comparing(item -> SemVer.parse(item.getPluginVersion())))
                    .toList();
            PluginMarketPublication latest = sorted.getLast();
            long downloads = versions.stream()
                    .mapToLong(item -> item.getDownloadCount() == null ? 0L : item.getDownloadCount())
                    .sum();
            LocalDateTime publishedAt = versions.stream()
                    .map(PluginMarketPublication::getCreateTime)
                    .filter(time -> time != null)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            LocalDateTime updatedAt = versions.stream()
                    .map(PluginMarketPublication::getCreateTime)
                    .filter(time -> time != null)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            return new PluginSummary(code,
                    StringUtils.hasText(latest.getDisplayName()) ? latest.getDisplayName() : code,
                    latest.getDescription(), latest.getIcon(), latest.getCategory(),
                    latest.getTags() == null ? List.of() : latest.getTags(), latest.getLicense(),
                    latest.getPublisherUserId(), latest.getPluginVersion(), downloads, publishedAt, updatedAt);
        }
    }

    // ---------- 内部实现 ----------

    private void requirePubliclyServed() {
        capabilityAppService.ensureEnabled(PluginMarketSourceAppService.CAPABILITY_CODE,
                PluginMarketSourceAppService.CAPABILITY_NAME);
    }

    private boolean reviewRequiredOf(CapabilityModule module) {
        String value = module.getConfig() == null ? null : module.getConfig().get("reviewRequired");
        // defaultConfig 只播种空配置行：读取处运行时回落默认值（默认需要审核）
        return !"false".equalsIgnoreCase(value);
    }

    private Optional<PluginMarketPublication> publishedPublication(String code, String pluginVersion) {
        if (!StringUtils.hasText(code) || !code.matches(CODE_PATTERN)
                || !StringUtils.hasText(pluginVersion) || !pluginVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            return Optional.empty();
        }
        return publicationRepo.findByCodeAndVersion(code, pluginVersion)
                .filter(PluginMarketPublication::published);
    }

    private Path jarFile(PluginMarketPublication publication) {
        Path path = marketDirectory().resolve(publication.getJarPath()).normalize();
        if (!path.startsWith(marketDirectory())) {
            throw new BizException("发布物文件路径非法");
        }
        return path;
    }

    private String siteName() {
        String siteName = settingAppService.publicSettings().get("siteName");
        return StringUtils.hasText(siteName) ? siteName : "YuDream 插件市场";
    }

    private PluginMarketPublicationDTO toDTO(PluginMarketPublication publication) {
        PluginMarketPublicationDTO dto = PluginMarketPublicationAssembler.toDTO(publication);
        dto.setCompatibility(readStringMap(publication.getCompatibilityJson()));
        return dto;
    }

    private Map<String, String> readStringMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (IOException e) {
            return Map.of();
        }
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

    private List<String> tagsFromMetadata(Map<String, Object> metadata) {
        if (metadata.get("tags") instanceof List<?> tags) {
            return tags.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        String trimmed = category.trim();
        if (!PluginMarketCategories.isValid(trimmed)) {
            throw new BizException("未知分类：" + trimmed + "，可选：" + String.join("、", PluginMarketCategories.ALL));
        }
        return trimmed;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String value = tag.trim().toLowerCase();
            if (value.length() > MAX_TAG_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
                throw new BizException("标签「" + value + "」超过 " + MAX_TAG_LENGTH + " 字符或包含控制字符");
            }
            normalized.add(value);
            if (normalized.size() > MAX_TAGS) {
                throw new BizException("标签最多 " + MAX_TAGS + " 个");
            }
        }
        return List.copyOf(normalized);
    }

    private Map<String, Object> compatibilityMap(Map<String, Object> metadata) {
        if (metadata.get("compatibility") instanceof Map<?, ?> compatibility) {
            Map<String, Object> result = new LinkedHashMap<>();
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
                result.put(key, range);
            }
            return result;
        }
        return Map.of();
    }

    private Map<String, Object> publisherMap(Map<String, Object> metadata) {
        if (metadata.get("publisher") instanceof Map<?, ?> publisher) {
            Map<String, Object> result = new LinkedHashMap<>();
            String id = text(publisher.get("id"));
            String name = text(publisher.get("name"));
            String url = text(publisher.get("url"));
            Object verified = publisher.get("verified");
            if (id == null && name == null && url == null && verified == null) {
                return Map.of();
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
            result.put("id", id);
            result.put("name", sanitizedName);
            result.put("url", url);
            result.put("verified", verifiedFlag);
            return result;
        }
        return Map.of();
    }

    private String license(Map<String, Object> metadata) {
        String license = text(metadata.get("license"));
        return license == null ? null : validateLicense(license);
    }

    private String validateLicense(String license) {
        String trimmed = license.trim();
        if (!trimmed.matches(LICENSE_PATTERN)) {
            throw new BizException("license 必须是 SPDX 表达式（如 MIT、Apache-2.0）");
        }
        return trimmed;
    }

    /** 生成契约 descriptor：兼容性/发布者来自保存的元数据，依赖按 require 语义展开。 */
    private String buildDescriptorJson(String code, String pluginVersion, String main, String displayName,
                                       String description, String icon, List<String> dependencies,
                                       List<String> softDependencies, String releaseNotes, String license,
                                       Map<String, Object> compatibility, Map<String, Object> publisher,
                                       String sha256) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("releaseVersion", pluginVersion);
            ObjectNode plugin = root.putObject("plugin");
            plugin.put("code", code);
            plugin.put("version", pluginVersion);
            plugin.put("main", main);
            if (StringUtils.hasText(displayName)) {
                plugin.put("displayName", displayName);
            }
            if (StringUtils.hasText(description)) {
                plugin.put("description", description);
            }
            // 消费端网关只接受 iconify 形态的 icon 内联值，其余（相对路径等）不下发
            if (icon != null && (icon.startsWith("i-") || icon.matches("^[A-Za-z0-9_-]+:[A-Za-z0-9_-]+$"))) {
                plugin.put("icon", icon);
            }
            if (publisher != null && !publisher.isEmpty()) {
                ObjectNode publisherNode = plugin.putObject("publisher");
                publisherNode.put("id", String.valueOf(publisher.get("id")));
                publisherNode.put("name", String.valueOf(publisher.get("name")));
                publisherNode.put("url", String.valueOf(publisher.get("url")));
                publisherNode.put("verified", Boolean.TRUE.equals(publisher.get("verified")));
            }
            if (StringUtils.hasText(license)) {
                plugin.put("license", license);
            }
            String notes = sanitizeReleaseNotes(releaseNotes);
            if (notes != null) {
                plugin.put("releaseNotes", notes);
            }
            ObjectNode compatibilityNode = compatibilityNode(compatibility);
            if (compatibilityNode != null) {
                plugin.set("compatibility", compatibilityNode);
            }
            ArrayNode dependencyNodes = plugin.putArray("dependencies");
            TreeSet<String> seen = new TreeSet<>();
            for (String dependency : dependencies == null ? List.<String>of() : dependencies) {
                if (StringUtils.hasText(dependency) && seen.add(dependency.trim())) {
                    ObjectNode node = dependencyNodes.addObject();
                    node.put("code", dependency.trim());
                    node.put("range", "x");
                    node.put("required", true);
                }
            }
            for (String dependency : softDependencies == null ? List.<String>of() : softDependencies) {
                if (StringUtils.hasText(dependency) && seen.add(dependency.trim())) {
                    ObjectNode node = dependencyNodes.addObject();
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

    private ObjectNode compatibilityNode(Map<String, Object> compatibility) {
        if (compatibility == null || compatibility.isEmpty()) {
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
                throw new BizException("compatibility." + key + " 不是合法的 SemVer 区间：" + range);
            }
            node.put(key, range);
            any = true;
        }
        return any ? node : null;
    }

    private void appendDependency(ArrayNode dependencies, List<String> codes, boolean required) {
        if (codes == null) {
            return;
        }
        for (String code : codes) {
            if (StringUtils.hasText(code)) {
                ObjectNode node = dependencies.addObject();
                node.put("code", code.trim());
                node.put("range", "x");
                node.put("required", required);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private String toJsonString(Map<String, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            return null;
        }
    }

    private String text(Object value) {
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            return null;
        }
        return stringValue.trim();
    }

    private String firstHasText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
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

    private LocalDateTime parseTime(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value.trim()).atStartOfDay();
            } catch (DateTimeParseException e) {
                throw new BizException(field + " 必须是 ISO-8601 日期或日期时间");
            }
        }
    }

    /** 分类是精确清单名，不做大小写归一。 */
    private Set<String> splitCsvExact(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private Set<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim().toLowerCase());
            }
        }
        return result;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
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

    private String toJson(ArrayNode node) {
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
