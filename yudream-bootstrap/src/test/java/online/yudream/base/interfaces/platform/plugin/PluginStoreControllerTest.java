package online.yudream.base.interfaces.platform.plugin;

import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdateDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdatePlanDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdateResultDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginCompatibilityDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDependencyDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDescriptorDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDetailDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginJarDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginVersionDTO;
import online.yudream.base.application.platform.plugin.service.PluginStoreAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.platform.plugin.controller.PluginMarketplaceController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PluginStoreControllerTest {

    @Mock
    private PluginStoreAppService pluginStoreAppService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PluginMarketplaceController(pluginStoreAppService)).build();
    }

    @Test
    void registersMarketplaceViewPermissionForBothEndpoints() throws Exception {
        assertEquals("platform:plugin-marketplace:view", PluginMarketplaceController.class
                .getMethod("list").getAnnotation(PermissionRegister.class).code());
        assertEquals("platform:plugin-marketplace:view", PluginMarketplaceController.class
                .getMethod("updates").getAnnotation(PermissionRegister.class).code());
        assertEquals("platform:plugin-marketplace:view", PluginMarketplaceController.class
                .getMethod("updatePlans").getAnnotation(PermissionRegister.class).code());
        assertEquals("platform:plugin-marketplace:view", PluginMarketplaceController.class
                .getMethod("updatePlan", String.class, String.class).getAnnotation(PermissionRegister.class).code());
        assertEquals("platform:plugin-marketplace:view", PluginMarketplaceController.class
                .getMethod("detail", String.class).getAnnotation(PermissionRegister.class).code());
        assertEquals("platform:plugin:manage", PluginMarketplaceController.class
                .getMethod("update", String.class, online.yudream.base.interfaces.platform.plugin.request.PluginMarketplaceUpdateRequest.class)
                .getAnnotation(PermissionRegister.class).code());
        assertEquals("platform:plugin:manage", PluginMarketplaceController.class
                .getMethod("rollback", String.class)
                .getAnnotation(PermissionRegister.class).code());
        assertEquals("platform:plugin:manage", PluginMarketplaceController.class
                .getMethod("install", String.class, online.yudream.base.interfaces.platform.plugin.request.PluginMarketplaceInstallRequest.class)
                .getAnnotation(PermissionRegister.class).code());
    }

    @Test
    void returnsInstalledPluginUpdatesInResultEnvelope() throws Exception {
        when(pluginStoreAppService.updates()).thenReturn(List.of(PluginMarketplaceUpdateDTO.builder()
                .code("demo")
                .currentVersion("1.0.0")
                .latestVersion("2.0.0")
                .latestReleaseVersion("2.0.0")
                .latestDisplayName("Demo 2")
                .updateAvailable(true)
                .compatible(false)
                .blockedReason("宿主版本不满足兼容性要求")
                .build()));

        mockMvc.perform(get("/api/platform/plugin-marketplace/updates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("demo"))
                .andExpect(jsonPath("$.data[0].currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data[0].latestVersion").value("2.0.0"))
                .andExpect(jsonPath("$.data[0].latestReleaseVersion").value("2.0.0"))
                .andExpect(jsonPath("$.data[0].latestDisplayName").value("Demo 2"))
                .andExpect(jsonPath("$.data[0].updateAvailable").value(true))
                .andExpect(jsonPath("$.data[0].compatible").value(false))
                .andExpect(jsonPath("$.data[0].blockedReason").value("宿主版本不满足兼容性要求"));

        verify(pluginStoreAppService).updates();
    }

    @Test
    void returnsReadOnlyUpdatePlanInResultEnvelope() throws Exception {
        when(pluginStoreAppService.updatePlan("demo", "2.0.0")).thenReturn(PluginMarketplaceUpdatePlanDTO.builder()
                .code("demo")
                .fromVersion("1.0.0")
                .toVersion("2.0.0")
                .changeType("MAJOR")
                .requiredDependencies(List.of(PluginStorePluginDependencyDTO.builder().code("base").range("^1.0.0").required(true).build()))
                .optionalDependencies(List.of(PluginStorePluginDependencyDTO.builder().code("optional").range("^2.0.0").required(false).build()))
                .affectedEnabledPlugins(List.of("dependent"))
                .requiresRestart(true)
                .blockedReason("必需依赖 base 不可用")
                .warnings(List.of("可选依赖 optional 不可用"))
                .build());

        mockMvc.perform(get("/api/platform/plugin-marketplace/{code}/update-plan", "demo")
                        .param("targetVersion", "2.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("demo"))
                .andExpect(jsonPath("$.data.fromVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.toVersion").value("2.0.0"))
                .andExpect(jsonPath("$.data.changeType").value("MAJOR"))
                .andExpect(jsonPath("$.data.requiredDependencies[0].code").value("base"))
                .andExpect(jsonPath("$.data.optionalDependencies[0].code").value("optional"))
                .andExpect(jsonPath("$.data.affectedEnabledPlugins[0]").value("dependent"))
                .andExpect(jsonPath("$.data.requiresRestart").value(true))
                .andExpect(jsonPath("$.data.blockedReason").value("必需依赖 base 不可用"))
                .andExpect(jsonPath("$.data.warnings[0]").value("可选依赖 optional 不可用"));

        verify(pluginStoreAppService).updatePlan("demo", "2.0.0");
    }

    @Test
    void returnsDefaultReadOnlyUpdatePlansFromStaticRoute() throws Exception {
        when(pluginStoreAppService.updatePlans()).thenReturn(List.of(PluginMarketplaceUpdatePlanDTO.builder()
                .code("demo")
                .fromVersion("1.0.0")
                .toVersion("2.0.0")
                .changeType("MAJOR")
                .requiredDependencies(List.of())
                .optionalDependencies(List.of())
                .affectedEnabledPlugins(List.of())
                .requiresRestart(true)
                .warnings(List.of())
                .build()));

        mockMvc.perform(get("/api/platform/plugin-marketplace/update-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("demo"))
                .andExpect(jsonPath("$.data[0].toVersion").value("2.0.0"))
                .andExpect(jsonPath("$.data[0].requiresRestart").value(true));

        verify(pluginStoreAppService).updatePlans();
    }

    @Test
    void forwardsAbsentTargetVersionForSingleUpdatePlan() throws Exception {
        when(pluginStoreAppService.updatePlan("demo", null)).thenReturn(PluginMarketplaceUpdatePlanDTO.builder()
                .code("demo")
                .fromVersion("1.0.0")
                .toVersion("2.0.0")
                .changeType("MAJOR")
                .requiredDependencies(List.of())
                .optionalDependencies(List.of())
                .affectedEnabledPlugins(List.of())
                .requiresRestart(true)
                .warnings(List.of())
                .build());

        mockMvc.perform(get("/api/platform/plugin-marketplace/{code}/update-plan", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("demo"));

        verify(pluginStoreAppService).updatePlan("demo", null);
    }

    @Test
    void confirmsUpdateAndReturnsRestartRequirement() throws Exception {
        when(pluginStoreAppService.update("demo", "2.0.0")).thenReturn(PluginMarketplaceUpdateResultDTO.builder()
                .modules(List.of(PluginModuleDTO.builder().code("demo").version("2.0.0").enabled(false).build()))
                .requiresRestart(true)
                .build());

        mockMvc.perform(post("/api/platform/plugin-marketplace/{code}/update", "demo")
                        .contentType("application/json")
                        .content("{\"releaseVersion\":\"2.0.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modules[0].code").value("demo"))
                .andExpect(jsonPath("$.data.modules[0].version").value("2.0.0"))
                .andExpect(jsonPath("$.data.modules[0].enabled").value(false))
                .andExpect(jsonPath("$.data.requiresRestart").value(true));

        verify(pluginStoreAppService).update("demo", "2.0.0");
    }

    @Test
    void confirmsRollbackAndReturnsRestartRequirement() throws Exception {
        when(pluginStoreAppService.rollback("demo")).thenReturn(PluginMarketplaceUpdateResultDTO.builder()
                .modules(List.of(PluginModuleDTO.builder().code("demo").version("1.0.0").enabled(false).build()))
                .requiresRestart(true)
                .build());

        mockMvc.perform(post("/api/platform/plugin-marketplace/{code}/rollback", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modules[0].version").value("1.0.0"))
                .andExpect(jsonPath("$.data.requiresRestart").value(true));

        verify(pluginStoreAppService).rollback("demo");
    }

    @Test
    void installsSpecifiedVersionWithoutEnablingIt() throws Exception {
        when(pluginStoreAppService.install("demo", "1.0.0")).thenReturn(List.of(PluginModuleDTO.builder()
                .code("demo")
                .version("1.0.0")
                .enabled(false)
                .build()));

        mockMvc.perform(post("/api/platform/plugin-marketplace/{code}/install", "demo")
                        .contentType("application/json")
                        .content("{\"releaseVersion\":\"1.0.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("demo"))
                .andExpect(jsonPath("$.data[0].version").value("1.0.0"))
                .andExpect(jsonPath("$.data[0].enabled").value(false));

        verify(pluginStoreAppService).install("demo", "1.0.0");
    }

    @Test
    void listsStorePluginsInResultEnvelope() throws Exception {
        when(pluginStoreAppService.list()).thenReturn(List.of(PluginStorePluginDTO.builder()
                .code("demo")
                .descriptor(descriptor("2.0.0", "demo", "Demo", "https://store.example.test/demo-2.jar"))
                .build()));

        mockMvc.perform(get("/api/platform/plugin-marketplace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("demo"))
                .andExpect(jsonPath("$.data[0].descriptor.releaseVersion").value("2.0.0"))
                .andExpect(jsonPath("$.data[0].descriptor.displayName").value("Demo"))
                .andExpect(jsonPath("$.data[0].descriptor.publisher.id").value("yudream"))
                .andExpect(jsonPath("$.data[0].descriptor.publisher.verified").value(true))
                .andExpect(jsonPath("$.data[0].descriptor.source.repository").value("https://github.com/yudream/demo"))
                .andExpect(jsonPath("$.data[0].descriptor.source.commit").value("0123456789abcdef0123456789abcdef01234567"))
                .andExpect(jsonPath("$.data[0].descriptor.license").value("Apache-2.0"))
                .andExpect(jsonPath("$.data[0].descriptor.releaseNotes").value("Bug fixes"))
                .andExpect(jsonPath("$.data[0].descriptor.compatibility.host").value(">=1.0.0 <2.0.0"))
                .andExpect(jsonPath("$.data[0].descriptor.compatibility.spi").value("^2.6.0"))
                .andExpect(jsonPath("$.data[0].descriptor.compatibility.frontendSdk").value("~1.0.0"))
                .andExpect(jsonPath("$.data[0].descriptor.dependencies[0].code").value("base"))
                .andExpect(jsonPath("$.data[0].descriptor.dependencies[0].range").value("1.2.x"))
                .andExpect(jsonPath("$.data[0].descriptor.dependencies[0].required").value(true))
                .andExpect(jsonPath("$.data[0].descriptor.jar.url").value("https://store.example.test/demo-2.jar"))
                .andExpect(jsonPath("$.data[0].index").doesNotExist());

        verify(pluginStoreAppService).list();
    }

    @Test
    void returnsStoreDetailInResultEnvelope() throws Exception {
        when(pluginStoreAppService.detail("demo")).thenReturn(PluginStorePluginDetailDTO.builder()
                .code("demo")
                .versions(List.of(
                        PluginStorePluginVersionDTO.builder()
                                .releaseVersion("1.0.0")
                                .descriptor(descriptor("1.0.0", "demo", "Demo 1", "https://store.example.test/demo-1.jar"))
                                .installable(true)
                                .build(),
                        PluginStorePluginVersionDTO.builder()
                                .releaseVersion("2.0.0")
                                .descriptor(descriptor("2.0.0", "demo", "Demo 2", "https://store.example.test/demo-2.jar"))
                                .installable(false)
                                .installDisabledReason("必需依赖 base 不可用")
                                .build()))
                .build());

        mockMvc.perform(get("/api/platform/plugin-marketplace/{code}", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("demo"))
                .andExpect(jsonPath("$.data.versions").isArray())
                .andExpect(jsonPath("$.data.versions[0].releaseVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.versions[0].installable").value(true))
                .andExpect(jsonPath("$.data.versions[0].installDisabledReason").doesNotExist())
                .andExpect(jsonPath("$.data.versions[0].descriptor.displayName").value("Demo 1"))
                .andExpect(jsonPath("$.data.versions[0].descriptor.jar.mavenCoordinates").value("example:demo:1.0.0"))
                .andExpect(jsonPath("$.data.versions[0].descriptor.jar.url").value("https://store.example.test/demo-1.jar"))
                .andExpect(jsonPath("$.data.versions[1].releaseVersion").value("2.0.0"))
                .andExpect(jsonPath("$.data.versions[1].installable").value(false))
                .andExpect(jsonPath("$.data.versions[1].installDisabledReason").value("必需依赖 base 不可用"))
                .andExpect(jsonPath("$.data.versions[1].descriptor.displayName").value("Demo 2"))
                .andExpect(jsonPath("$.data.versions[1].descriptor.jar.mavenCoordinates").value("example:demo:2.0.0"))
                .andExpect(jsonPath("$.data.versions[1].descriptor.jar.url").value("https://store.example.test/demo-2.jar"));

        verify(pluginStoreAppService).detail("demo");
    }

    private PluginStorePluginDescriptorDTO descriptor(String version, String code, String displayName, String jarUrl) {
        return PluginStorePluginDescriptorDTO.builder()
                .releaseVersion(version)
                .code(code)
                .version(version)
                .main("example.Plugin")
                .displayName(displayName)
                .description("Plugin description")
                .icon("https://store.example.test/icon.svg")
                .screenshots(List.of("https://store.example.test/screenshot.png"))
                .publisher(online.yudream.base.application.platform.plugin.dto.PluginStorePluginPublisherDTO.builder()
                        .id("yudream")
                        .name("YuDream")
                        .url("https://yudream.online")
                        .verified(true)
                        .build())
                .source(online.yudream.base.application.platform.plugin.dto.PluginStorePluginSourceDTO.builder()
                        .repository("https://github.com/yudream/demo")
                        .commit("0123456789abcdef0123456789abcdef01234567")
                        .build())
                .license("Apache-2.0")
                .releaseNotes("Bug fixes")
                .compatibility(PluginStorePluginCompatibilityDTO.builder()
                        .host(">=1.0.0 <2.0.0")
                        .spi("^2.6.0")
                        .frontendSdk("~1.0.0")
                        .build())
                .dependencies(List.of(PluginStorePluginDependencyDTO.builder()
                        .code("base")
                        .range("1.2.x")
                        .required(true)
                        .build()))
                .jar(PluginStorePluginJarDTO.builder()
                        .mavenCoordinates("example:demo:" + version)
                        .url(jarUrl)
                        .sha256("a".repeat(64))
                        .build())
                .build();
    }
}
