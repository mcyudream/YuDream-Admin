package online.yudream.base.application.platform.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationReviewCmd;
import online.yudream.base.application.platform.plugin.dto.PluginMarketPublicationDTO;
import online.yudream.base.application.platform.plugin.service.PluginMarketPublicationAppService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @TempDir
    Path tempDir;

    private PluginMarketPublicationAppService service;

    @BeforeEach
    void setUp() {
        service = new PluginMarketPublicationAppService(publicationRepo, pluginRuntimeGateway,
                capabilityAppService, capabilityModuleRepo, new ObjectMapper());
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
                9L, true);

        assertEquals("demo", result.getCode());
        assertEquals("1.0.0", result.getPluginVersion());
        assertEquals(PluginPublicationStatus.PENDING, result.getStatus());
        assertEquals(9L, result.getPublisherUserId());

        JsonNode descriptor = new ObjectMapper().readTree(resultToSavedPublication().getDescriptorJson());
        assertEquals(1, descriptor.get("schemaVersion").asInt());
        assertEquals("1.0.0", descriptor.get("releaseVersion").asText());
        assertEquals("demo", descriptor.get("plugin").get("code").asText());
        assertEquals("MIT", descriptor.get("plugin").get("license").asText());
        assertEquals("^1.0.0", descriptor.get("plugin").get("compatibility").get("host").asText());
        assertEquals("YuDream", descriptor.get("plugin").get("publisher").get("name").asText());
        // plugin.yml 依赖展开为无区间约束的契约依赖；jar 引用相对插件 index 目录
        assertEquals("base", descriptor.get("plugin").get("dependencies").get(0).get("code").asText());
        assertEquals("x", descriptor.get("plugin").get("dependencies").get(0).get("range").asText());
        assertTrue(descriptor.get("plugin").get("dependencies").get(0).get("required").asBoolean());
        assertEquals("opt", descriptor.get("plugin").get("dependencies").get(1).get("code").asText());
        assertFalse(descriptor.get("plugin").get("dependencies").get(1).get("required").asBoolean());
        assertEquals("versions/1.0.0/plugin.jar", descriptor.get("jar").get("url").asText());
        assertEquals(64, descriptor.get("jar").get("sha256").asText().length());
        // JAR 已落盘到市场目录
        assertTrue(Files.isRegularFile(tempDir.resolve("demo").resolve("1.0.0").resolve("plugin.jar")));
        verify(capabilityAppService).ensureEnabled(anyString(), anyString());
    }

    @Test
    void publishSkipsReviewWhenConfiguredOff() {
        stubDescribe(descriptor());
        stubReviewRequired("false");
        when(publicationRepo.findByCodeAndVersion("demo", "1.0.0")).thenReturn(Optional.empty());
        when(publicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PluginMarketPublicationDTO result = service.publish(stream(), JAR_BYTES.length, null, null, 9L, false);

        assertEquals(PluginPublicationStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void publishRejectsDuplicateVersionWithoutSaving() {
        stubDescribe(descriptor());
        stubReviewRequired("true");
        when(publicationRepo.findByCodeAndVersion("demo", "1.0.0")).thenReturn(Optional.of(PluginMarketPublication.builder().build()));

        assertThrows(BizException.class, () -> service.publish(stream(), JAR_BYTES.length, null, null, 9L, false));

        verify(publicationRepo, never()).save(any());
    }

    @Test
    void publishRejectsInvalidPluginVersionsAndOversize() {
        stubDescribe(new PluginDescriptorInfo("demo", "Demo", "1.0.0-beta", null, "example.Plugin", null, List.of(), List.of()));
        assertThrows(BizException.class, () -> service.publish(stream(), JAR_BYTES.length, null, null, 9L, false));

        assertThrows(BizException.class, () -> service.publish(stream(), 0, null, null, 9L, false));
        assertThrows(BizException.class, () -> service.publish(stream(), maxJarBytes() + 1, null, null, 9L, false));

        verify(publicationRepo, never()).save(any());
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
        var revoked = service.unpublish(reviewCmd(2L, null), 7L);
        assertEquals(PluginPublicationStatus.REVOKED, revoked.getStatus());
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
    void rootIndexListsDistinctPublishedCodesAndCodeIndexSortsAscending() throws Exception {
        when(publicationRepo.findByStatus(PluginPublicationStatus.PUBLISHED)).thenReturn(List.of(
                publication("beta", "2.0.0"),
                publication("alpha", "1.0.0"),
                publication("beta", "1.0.0"),
                publication("beta", "1.2.0")));

        JsonNode root = new ObjectMapper().readTree(service.rootIndexJson());
        assertEquals(List.of("alpha", "beta"), textList(root.get("plugins"), "code"));
        assertEquals("beta/index.json", root.get("plugins").get(1).get("index").asText());

        JsonNode index = new ObjectMapper().readTree(service.codeIndexJson("beta").orElseThrow());
        assertEquals("beta", index.get("pluginCode").asText());
        assertEquals(List.of("1.0.0", "1.2.0", "2.0.0"), textList(index.get("versions"), "releaseVersion"));
        assertEquals("versions/2.0.0/descriptor.json", index.get("versions").get(2).get("descriptor").asText());

        assertTrue(service.codeIndexJson("missing").isEmpty());
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

    private PluginMarketPublication publication(String code, String pluginVersion) {
        return PluginMarketPublication.builder()
                .code(code)
                .pluginVersion(pluginVersion)
                .descriptorJson("{}")
                .jarPath(code + "/" + pluginVersion + "/plugin.jar")
                .status(PluginPublicationStatus.PUBLISHED)
                .build();
    }
}
