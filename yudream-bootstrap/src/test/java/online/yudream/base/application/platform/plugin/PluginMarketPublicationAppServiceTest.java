package online.yudream.base.application.platform.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationEditCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationReviewCmd;
import online.yudream.base.application.platform.plugin.dto.PluginMarketPublicationDTO;
import online.yudream.base.application.platform.plugin.query.PluginMarketPublicationPageQuery;
import online.yudream.base.application.platform.plugin.service.PluginMarketPublicationAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.application.platform.plugin.service.PluginUserCatalogAppService;
import online.yudream.base.application.system.setting.service.SettingAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketPublicationRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginMarketPublicationAppServiceTest {

    private static final byte[] JAR_BYTES = "plugin-jar-bytes".getBytes(StandardCharsets.UTF_8);

    @Mock
    private PluginMarketPublicationRepo publicationRepo;

    @Mock
    private PluginRuntimeGateway pluginRuntimeGateway;

    @Mock
    private CapabilityAppService capabilityAppService;

    @Mock
    private CapabilityModuleRepo capabilityModuleRepo;

    @Mock
    private PluginUserCatalogAppService pluginUserCatalogAppService;

    @Mock
    private SettingAppService settingAppService;

    @TempDir
    Path tempDir;

    private PluginMarketPublicationAppService service;

    @BeforeEach
    void setUp() {
        service = new PluginMarketPublicationAppService(publicationRepo, pluginRuntimeGateway,
                capabilityAppService, capabilityModuleRepo, pluginUserCatalogAppService, settingAppService,
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "marketDirectory", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxJarBytes", 1024L * 1024);
    }

    @Test
    void publishParsesJarGeneratesDescriptorAndGoesToPendingWhenReviewRequired() throws Exception {
        stubDescribe(descriptor());
        stubReviewRequired("true");
        when(publicationRepo.findByCodeAndVersion("demo", "1.0.0")).thenReturn(Optional.empty());
        when(publicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PluginMarketPublicationDTO result = service.publish(stream(), JAR_BYTES.length, "修复若干问题",
                "{\"license\":\"MIT\",\"compatibility\":{\"host\":\"^1.0.0\"},\"publisher\":{\"id\":\"yudream\",\"name\":\"YuDream\",\"url\":\"https://yudream.online\",\"verified\":true}}",
                "效率工具", List.of("Demo", "demo", "Market"), 9L, true, false);

        assertEquals("demo", result.getCode());
        assertEquals("1.0.0", result.getPluginVersion());
        assertEquals(PluginPublicationStatus.PENDING, result.getStatus());
        assertEquals(9L, result.getPublisherUserId());

        PluginMarketPublication saved = resultToSavedPublication();
        // 标签规范化：统一小写并去重
        assertEquals(List.of("demo", "market"), saved.getTags());
        assertEquals("效率工具", saved.getCategory());
        JsonNode descriptor = new ObjectMapper().readTree(saved.getDescriptorJson());
        assertEquals(1, descriptor.get("schemaVersion").asInt());
        assertEquals("1.0.0", descriptor.get("releaseVersion").asText());
        assertEquals("demo", descriptor.get("plugin").get("code").asText());
        assertEquals("MIT", descriptor.get("plugin").get("license").asText());
        assertEquals("^1.0.0", descriptor.get("plugin").get("compatibility").get("host").asText());
        assertEquals("YuDream", descriptor.get("plugin").get("publisher").get("name").asText());
        assertEquals("base", descriptor.get("plugin").get("dependencies").get(0).get("code").asText());
        assertEquals("x", descriptor.get("plugin").get("dependencies").get(0).get("range").asText());
        assertTrue(descriptor.get("plugin").get("dependencies").get(0).get("required").asBoolean());
        assertEquals("opt", descriptor.get("plugin").get("dependencies").get(1).get("code").asText());
        assertFalse(descriptor.get("plugin").get("dependencies").get(1).get("required").asBoolean());
        assertEquals("versions/1.0.0/plugin.jar", descriptor.get("jar").get("url").asText());
        assertEquals(64, descriptor.get("jar").get("sha256").asText().length());
        assertTrue(Files.isRegularFile(tempDir.resolve("demo").resolve("1.0.0").resolve("plugin.jar")));
        verify(capabilityAppService).ensureEnabled(anyString(), anyString());
    }

    @Test
    void publishSkipsReviewWhenConfiguredOff() {
        stubDescribe(descriptor());
        stubReviewRequired("false");
        when(publicationRepo.findByCodeAndVersion("demo", "1.0.0")).thenReturn(Optional.empty());
        when(publicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PluginMarketPublicationDTO result = service.publish(stream(), JAR_BYTES.length, null, null,
                null, null, 9L, false, false);

        assertEquals(PluginPublicationStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void publishRejectsDuplicateVersionUnknownCategoryAndInvalidTags() {
        stubDescribe(descriptor());
        stubReviewRequired("true");
        when(publicationRepo.findByCodeAndVersion("demo", "1.0.0")).thenReturn(Optional.of(PluginMarketPublication.builder().build()));

        assertThrows(BizException.class, () -> service.publish(stream(), JAR_BYTES.length, null, null,
                null, null, 9L, false, false));
        verify(publicationRepo, never()).save(any());

        when(publicationRepo.findByCodeAndVersion("demo", "1.0.0")).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.publish(stream(), JAR_BYTES.length, null, null,
                "不存在的分类", null, 9L, false, false));
        assertThrows(BizException.class, () -> service.publish(stream(), JAR_BYTES.length, null, null,
                null, List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"), 9L, false, false));
        verify(publicationRepo, never()).save(any());
    }

    @Test
    void publishRejectsInvalidPluginVersionsAndOversize() {
        stubDescribe(new PluginDescriptorInfo("demo", "Demo", "1.0.0-beta", null, "example.Plugin", null, List.of(), List.of()));
        assertThrows(BizException.class, () -> service.publish(stream(), JAR_BYTES.length, null, null,
                null, null, 9L, false, false));

        assertThrows(BizException.class, () -> service.publish(stream(), 0, null, null, null, null, 9L, false, false));
        assertThrows(BizException.class, () -> service.publish(stream(), maxJarBytes() + 1, null, null, null, null, 9L, false, false));

        verify(publicationRepo, never()).save(any());
    }

    @Test
    void editUpdatesMetadataAndRegeneratesDescriptorWithoutResettingStatus() throws Exception {
        PluginMarketPublication published = publication("demo", "1.0.0", PluginPublicationStatus.PUBLISHED);
        published.setDisplayName("旧名称");
        published.setMainClass("example.Plugin");
        published.setSha256("a".repeat(64));
        published.setDescriptorJson("{\"schemaVersion\":1}");
        when(publicationRepo.findById(1L)).thenReturn(Optional.of(published));
        when(publicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PluginMarketPublicationEditCmd cmd = new PluginMarketPublicationEditCmd();
        cmd.setId(1L);
        cmd.setDisplayName("新名称");
        cmd.setCategory("Minecraft");
        cmd.setTags(List.of("New Tag"));
        cmd.setCompatibility(Map.of("host", "^2.0.0"));

        PluginMarketPublicationDTO result = service.edit(cmd, 1L, false);

        assertEquals("新名称", result.getDisplayName());
        assertEquals("Minecraft", result.getCategory());
        assertEquals(List.of("new tag"), result.getTags());
        assertEquals(PluginPublicationStatus.PUBLISHED, result.getStatus());
        org.mockito.ArgumentCaptor<PluginMarketPublication> captor =
                org.mockito.ArgumentCaptor.forClass(PluginMarketPublication.class);
        verify(publicationRepo).save(captor.capture());
        JsonNode descriptor = new ObjectMapper().readTree(captor.getValue().getDescriptorJson());
        assertEquals("新名称", descriptor.get("plugin").get("displayName").asText());
        assertEquals("^2.0.0", descriptor.get("plugin").get("compatibility").get("host").asText());
    }

    @Test
    void deleteRemovesRowAndJarFile() throws Exception {
        PluginMarketPublication publication = publication("demo", "1.0.0", PluginPublicationStatus.PUBLISHED);
        publication.setId(1L);
        publication.setJarPath("demo/1.0.0/plugin.jar");
        when(publicationRepo.findById(1L)).thenReturn(Optional.of(publication));
        Path jar = tempDir.resolve("demo").resolve("1.0.0").resolve("plugin.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "jar");

        service.delete(1L, 1L, false);

        verify(publicationRepo).deleteById(1L);
        assertFalse(Files.exists(jar));
    }

    @Test
    void reviewTransitionsFollowAggregateStateMachine() {
        PluginMarketPublication pending = PluginMarketPublication.builder()
                .id(1L).code("demo").pluginVersion("1.0.0").status(PluginPublicationStatus.PENDING).build();
        when(publicationRepo.findById(1L)).thenReturn(Optional.of(pending));
        when(publicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var accepted = service.accept(reviewCmd(1L, "质量没问题"), 7L);
        assertEquals(PluginPublicationStatus.PUBLISHED, accepted.getStatus());
        assertEquals(7L, accepted.getReviewerUserId());
        assertEquals("质量没问题", accepted.getReviewNote());

        assertThrows(BizException.class, () -> service.reject(reviewCmd(1L, null), 7L));

        PluginMarketPublication published = PluginMarketPublication.builder()
                .id(2L).code("demo").pluginVersion("2.0.0").status(PluginPublicationStatus.PUBLISHED).build();
        when(publicationRepo.findById(2L)).thenReturn(Optional.of(published));
        var revoked = service.unpublish(reviewCmd(2L, null), 7L, true);
        assertEquals(PluginPublicationStatus.REVOKED, revoked.getStatus());
    }

    @Test
    void publishSkipsReviewWhenOperatorHasPublishPermission() {
        stubDescribe(descriptor());
        stubReviewRequired("true");
        when(publicationRepo.findByCodeAndVersion("demo", "1.0.0")).thenReturn(Optional.empty());
        when(publicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PluginMarketPublicationDTO result = service.publish(stream(), JAR_BYTES.length, null, null,
                null, null, 9L, false, true);

        assertEquals(PluginPublicationStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void pageFiltersMineAndStatus() {
        PluginMarketPublication mine = publication("demo", "1.0.0", PluginPublicationStatus.PENDING);
        mine.setPublisherUserId(9L);
        when(publicationRepo.page(PluginPublicationStatus.PENDING, 9L, 1, 10))
                .thenReturn(new PageResult<>(List.of(mine), 1, 1, 10));

        PluginMarketPublicationPageQuery query = new PluginMarketPublicationPageQuery();
        query.setStatus("PENDING");
        query.setMine(true);
        query.setPage(1);
        query.setSize(10);

        PageResult<PluginMarketPublicationDTO> page = service.page(query, 9L, true);
        assertEquals(1, page.getTotal());
        assertEquals("demo", page.getRecords().getFirst().getCode());
        verify(publicationRepo).page(PluginPublicationStatus.PENDING, 9L, 1, 10);
    }

    @Test
    void pageForcesMineWhenOperatorCannotViewAll() {
        when(publicationRepo.page(null, 9L, 1, 10))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        service.page(new PluginMarketPublicationPageQuery(), 9L, false);

        verify(publicationRepo).page(null, 9L, 1, 10);
    }

    @Test
    void editAndDeleteRejectForeignPublicationsWithoutManagePermission() {
        PluginMarketPublication ownedByOther = publication("demo", "1.0.0", PluginPublicationStatus.PUBLISHED);
        ownedByOther.setId(1L);
        ownedByOther.setPublisherUserId(2L);
        when(publicationRepo.findById(1L)).thenReturn(Optional.of(ownedByOther));

        PluginMarketPublicationEditCmd cmd = new PluginMarketPublicationEditCmd();
        cmd.setId(1L);
        cmd.setDisplayName("新名称");

        assertThrows(BizException.class, () -> service.edit(cmd, 9L, false));
        assertThrows(BizException.class, () -> service.delete(1L, 9L, false));
        assertThrows(BizException.class, () -> service.unpublish(reviewCmd(1L, null), 9L, false));
        verify(publicationRepo, never()).save(any());
        verify(publicationRepo, never()).deleteById(any());
    }

    @Test
    void reviewRequiredFallsBackToTrueWithoutConfiguration() {
        when(capabilityModuleRepo.findByCode(anyString())).thenReturn(Optional.empty());
        assertTrue(service.reviewRequired());

        when(capabilityModuleRepo.findByCode(anyString())).thenReturn(Optional.of(module(null)));
        assertTrue(service.reviewRequired());

        when(capabilityModuleRepo.findByCode(anyString())).thenReturn(Optional.of(module(Map.of("reviewRequired", "false"))));
        assertFalse(service.reviewRequired());
    }

    @Test
    void publicEnabledFallsBackToTrueWithoutConfiguration() {
        when(capabilityModuleRepo.findByCode(anyString())).thenReturn(Optional.empty());
        assertTrue(service.publicEnabled());

        when(capabilityModuleRepo.findByCode(anyString())).thenReturn(Optional.of(module(null)));
        assertTrue(service.publicEnabled());

        when(capabilityModuleRepo.findByCode(anyString())).thenReturn(Optional.of(module(Map.of("publicEnabled", "false"))));
        assertFalse(service.publicEnabled());
    }

    @Test
    void publicCatalogStopsWhenPublicMarketDisabled() {
        when(capabilityModuleRepo.findByCode(anyString())).thenReturn(Optional.of(module(Map.of("publicEnabled", "false"))));
        assertThrows(BizException.class, () -> service.manifestJson());
        verify(publicationRepo, never()).findByStatus(any());
    }

    @Test
    void localCatalogStillServesWhenPublicMarketDisabled() {
        PluginMarketPublication published = publication("demo", "1.0.0", PluginPublicationStatus.PUBLISHED);
        published.setJarPath("demo/1.0.0/plugin.jar");
        when(publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)).thenReturn(List.of(published));

        var entries = service.localCatalogEntries();
        assertEquals(1, entries.size());
        assertEquals("demo", entries.getFirst().code());
    }

    @Test
    void manifestAndFacetsAggregatePublishedCatalog() throws Exception {
        when(settingAppService.publicSettings()).thenReturn(Map.of("siteName", "测试站"));
        when(publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)).thenReturn(List.of(
                publication("beta", "2.0.0", PluginPublicationStatus.PUBLISHED),
                publication("alpha", "1.0.0", PluginPublicationStatus.PUBLISHED),
                publication("beta", "1.0.0", PluginPublicationStatus.PUBLISHED)));

        JsonNode manifest = new ObjectMapper().readTree(service.manifestJson());
        assertEquals("yudream-market-v2", manifest.get("protocol").asText());
        assertEquals("测试站", manifest.get("name").asText());
        assertEquals(2, manifest.get("pluginCount").asInt());

        JsonNode categories = new ObjectMapper().readTree(service.categoriesJson());
        assertTrue(categories.isArray());

        JsonNode tags = new ObjectMapper().readTree(service.tagsJson(10));
        assertTrue(tags.isArray());
    }

    @Test
    void pagePluginsGroupsSortsFiltersAndPaginates() throws Exception {
        PluginMarketPublication betaNew = publication("beta", "2.0.0", PluginPublicationStatus.PUBLISHED);
        betaNew.setDownloadCount(30L);
        betaNew.setCreateTime(LocalDateTime.of(2026, 9, 1, 0, 0));
        betaNew.setCategory("Minecraft");
        PluginMarketPublication betaOld = publication("beta", "1.0.0", PluginPublicationStatus.PUBLISHED);
        betaOld.setDownloadCount(12L);
        PluginMarketPublication alpha = publication("alpha", "1.0.0", PluginPublicationStatus.PUBLISHED);
        alpha.setDownloadCount(100L);
        alpha.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        alpha.setCategory("AI 与对话");
        alpha.setTags(List.of("chat"));
        when(publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED))
                .thenReturn(List.of(betaNew, betaOld, alpha));

        // 默认 newest：beta（2026-09）在 alpha（2026-01）前，同 code 分组合并下载量
        JsonNode byNewest = new ObjectMapper().readTree(service.pagePluginsJson(
                null, null, null, null, null, null, null, 1, 20));
        assertEquals(2, byNewest.get("total").asInt());
        assertEquals("beta", byNewest.get("items").get(0).get("code").asText());
        assertEquals(42L, byNewest.get("items").get(0).get("downloads").asLong());
        assertEquals("2.0.0", byNewest.get("items").get(0).get("latestVersion").asText());

        // downloads 排序：alpha（100）在前
        JsonNode byDownloads = new ObjectMapper().readTree(service.pagePluginsJson(
                null, null, null, null, null, null, "downloads", 1, 20));
        assertEquals("alpha", byDownloads.get("items").get(0).get("code").asText());

        // 分类过滤
        JsonNode byCategory = new ObjectMapper().readTree(service.pagePluginsJson(
                null, "Minecraft", null, null, null, null, null, 1, 20));
        assertEquals(1, byCategory.get("total").asInt());
        assertEquals("beta", byCategory.get("items").get(0).get("code").asText());

        // 标签过滤（大小写不敏感）
        JsonNode byTag = new ObjectMapper().readTree(service.pagePluginsJson(
                null, null, "CHAT", null, null, null, null, 1, 20));
        assertEquals(1, byTag.get("total").asInt());

        // 作者 + 时间过滤
        JsonNode byAuthor = new ObjectMapper().readTree(service.pagePluginsJson(
                null, null, null, 9L, null, null, null, 1, 20));
        assertEquals(0, byAuthor.get("total").asInt());
        JsonNode byTime = new ObjectMapper().readTree(service.pagePluginsJson(
                null, null, null, null, "2026-06-01", null, null, 1, 20));
        assertEquals(1, byTime.get("total").asInt());

        // 关键词与分页
        JsonNode searched = new ObjectMapper().readTree(service.pagePluginsJson(
                "ALPHA", null, null, null, null, null, null, 1, 20));
        assertEquals(1, searched.get("total").asInt());
        JsonNode paged = new ObjectMapper().readTree(service.pagePluginsJson(
                null, null, null, null, null, null, null, 2, 1));
        assertEquals(2, paged.get("total").asInt());
        assertEquals(1, paged.get("items").size());

        assertThrows(BizException.class, () -> service.pagePluginsJson(
                null, null, null, null, null, null, "bogus", 1, 20));
    }

    @Test
    void rootIndexListsDistinctPublishedCodesAndCodeIndexSortsAscending() throws Exception {
        when(publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)).thenReturn(List.of(
                publication("beta", "2.0.0", PluginPublicationStatus.PUBLISHED),
                publication("alpha", "1.0.0", PluginPublicationStatus.PUBLISHED),
                publication("beta", "1.0.0", PluginPublicationStatus.PUBLISHED),
                publication("beta", "1.2.0", PluginPublicationStatus.PUBLISHED)));

        JsonNode root = new ObjectMapper().readTree(service.rootIndexJson());
        assertEquals(List.of("alpha", "beta"), textList(root.get("plugins"), "code"));
        assertEquals("beta/index.json", root.get("plugins").get(1).get("index").asText());

        JsonNode index = new ObjectMapper().readTree(service.codeIndexJson("beta").orElseThrow());
        assertEquals("beta", index.get("pluginCode").asText());
        assertEquals(List.of("1.0.0", "1.2.0", "2.0.0"), textList(index.get("versions"), "releaseVersion"));
        assertEquals("versions/2.0.0/descriptor.json", index.get("versions").get(2).get("descriptor").asText());

        assertTrue(service.codeIndexJson("missing").isEmpty());
    }

    @Test
    void localCatalogEntriesGroupPublishedVersionsAndResolveLocalJar() throws Exception {
        PluginMarketPublication v1 = publication("demo", "1.0.0", PluginPublicationStatus.PUBLISHED);
        v1.setMainClass("example.Plugin");
        v1.setJarPath("demo/1.0.0/plugin.jar");
        PluginMarketPublication v2 = publication("demo", "2.0.0", PluginPublicationStatus.PUBLISHED);
        v2.setMainClass("example.Plugin");
        v2.setJarPath("demo/2.0.0/plugin.jar");
        when(publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)).thenReturn(List.of(v2, v1));

        var entries = service.localCatalogEntries();
        assertEquals(1, entries.size());
        assertEquals("demo", entries.getFirst().code());
        assertEquals(List.of("1.0.0", "2.0.0"), entries.getFirst().structuredVersions().stream()
                .map(online.yudream.base.domain.platform.plugin.valobj.PluginStoreStructuredVersion::releaseVersion)
                .toList());
        assertEquals("local:demo/2.0.0/plugin.jar", entries.getFirst().structuredVersions().getLast().downloadUrl());

        Path jar = tempDir.resolve("demo").resolve("1.0.0").resolve("plugin.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "jar");
        assertEquals(jar, service.resolveLocalJar("local:demo/1.0.0/plugin.jar"));
        assertThrows(BizException.class, () -> service.resolveLocalJar("local:../escape.jar"));
        assertThrows(BizException.class, () -> service.resolveLocalJar("https://evil.example/a.jar"));
    }

    private List<String> textList(JsonNode array, String field) {
        return array == null ? List.of() : java.util.stream.StreamSupport
                .stream(array.spliterator(), false)
                .map(node -> node.get(field).asText())
                .toList();
    }

    private PluginMarketPublication resultToSavedPublication() {
        org.mockito.ArgumentCaptor<PluginMarketPublication> captor =
                org.mockito.ArgumentCaptor.forClass(PluginMarketPublication.class);
        verify(publicationRepo).save(captor.capture());
        return captor.getValue();
    }

    private PluginMarketPublicationReviewCmd reviewCmd(Long id, String note) {
        PluginMarketPublicationReviewCmd cmd = new PluginMarketPublicationReviewCmd();
        cmd.setId(id);
        cmd.setNote(note);
        return cmd;
    }

    private long maxJarBytes() {
        return 1024L * 1024;
    }

    private InputStream stream() {
        return new ByteArrayInputStream(JAR_BYTES);
    }

    private PluginDescriptorInfo descriptor() {
        return new PluginDescriptorInfo("demo", "Demo 插件", "1.0.0", "示例插件", "example.Plugin",
                null, List.of("base"), List.of("opt"));
    }

    private void stubDescribe(PluginDescriptorInfo descriptor) {
        lenient().when(pluginRuntimeGateway.describe(any())).thenReturn(Optional.of(descriptor));
    }

    private void stubReviewRequired(String configured) {
        lenient().when(capabilityModuleRepo.findByCode(anyString()))
                .thenReturn(Optional.of(module(configured == null ? null : Map.of("reviewRequired", configured))));
    }

    private CapabilityModule module(Map<String, String> config) {
        return CapabilityModule.builder()
                .code("plugin-market-source")
                .config(config == null ? new HashMap<>() : new HashMap<>(config))
                .build();
    }

    private PluginMarketPublication publication(String code, String pluginVersion, PluginPublicationStatus status) {
        return PluginMarketPublication.builder()
                .code(code)
                .pluginVersion(pluginVersion)
                .displayName(code + " 插件")
                .mainClass("example." + code)
                .sha256("a".repeat(64))
                .descriptorJson("{}")
                .jarPath(code + "/" + pluginVersion + "/plugin.jar")
                .publisherUserId(1L)
                .status(status)
                .build();
    }
}
