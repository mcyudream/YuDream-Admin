package online.yudream.base.interfaces.platform.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.plugin.dto.PluginMarketSourceDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketSourceTestResultDTO;
import online.yudream.base.application.platform.plugin.service.PluginMarketSourceAppService;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceSyncStatus;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.platform.plugin.controller.PluginMarketSourceController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PluginMarketSourceControllerTest {

    @Mock
    private PluginMarketSourceAppService pluginMarketSourceAppService;

    @Test
    void registersExpectedPermissionCodes() {
        for (Method method : PluginMarketSourceController.class.getDeclaredMethods()) {
            PermissionRegister register = method.getAnnotation(PermissionRegister.class);
            if (register == null) {
                continue;
            }
            assertTrue(register.code().startsWith("platform:plugin-market-source:"),
                    () -> method.getName() + " 使用了非市场源权限码：" + register.code());
            assertEquals("平台插件市场源", register.module());
        }
    }

    @Test
    void listsSourcesWithMaskedToken() throws Exception {
        when(pluginMarketSourceAppService.list()).thenReturn(List.of(PluginMarketSourceDTO.builder()
                .id(1L)
                .code("default")
                .name("本机插件市场")
                .type(online.yudream.base.domain.platform.plugin.enumerate.MarketSourceType.LOCAL)
                .rootUrl(null)
                .tokenConfigured(true)
                .enabled(true)
                .builtIn(true)
                .sortOrder(0)
                .syncStatus(MarketSourceSyncStatus.OK)
                .pluginCount(12)
                .build()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PluginMarketSourceController(pluginMarketSourceAppService)).build();

        mockMvc.perform(get("/api/platform/plugin-market-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].code").value("default"))
                .andExpect(jsonPath("$.data[0].type").value("LOCAL"))
                .andExpect(jsonPath("$.data[0].tokenConfigured").value(true))
                .andExpect(jsonPath("$.data[0].pluginCount").value(12));
    }

    @Test
    void testsUnsavedSourceForm() throws Exception {
        when(pluginMarketSourceAppService.test(any())).thenReturn(PluginMarketSourceTestResultDTO.builder()
                .ok(true)
                .pluginCount(3)
                .message("连接成功，发现 3 个插件")
                .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PluginMarketSourceController(pluginMarketSourceAppService)).build();

        mockMvc.perform(post("/api/platform/plugin-market-sources/test")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsString(java.util.Map.of(
                                "rootUrl", "https://store.example.test/index.json"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.pluginCount").value(3));
    }
}
