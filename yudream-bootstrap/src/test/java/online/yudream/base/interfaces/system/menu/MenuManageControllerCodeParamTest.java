package online.yudream.base.interfaces.system.menu;

import online.yudream.base.application.system.menu.cmd.MenuUpdateCmd;
import online.yudream.base.application.system.menu.dto.MenuManageDTO;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.interfaces.system.menu.controller.MenuManageController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MenuManageControllerCodeParamTest {

    private static final String PLUGIN_MENU_CODE = "plugin:demo/pages/home";
    private static final String LEGACY_MENU_CODE = "system:legacy-menu";

    @Mock
    private MenuAppService menuAppService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MenuManageController(menuAppService)).build();
    }

    @Test
    void updateAcceptsPluginMenuCodeContainingSlashAndColonAsQueryParam() throws Exception {
        when(menuAppService.update(any(MenuUpdateCmd.class))).thenReturn(MenuManageDTO.builder()
                .code(PLUGIN_MENU_CODE)
                .name("Plugin page")
                .type(MenuNodeType.MENU)
                .status(MenuStatus.ACTIVE)
                .build());

        mockMvc.perform(put("/api/system/menus")
                        .param("code", PLUGIN_MENU_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Plugin page\",\"type\":\"MENU\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MenuUpdateCmd> commandCaptor = ArgumentCaptor.forClass(MenuUpdateCmd.class);
        verify(menuAppService).update(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getCode()).isEqualTo(PLUGIN_MENU_CODE);
    }

    @Test
    void disableAcceptsPluginMenuCodeContainingSlashAndColonAsQueryParam() throws Exception {
        mockMvc.perform(delete("/api/system/menus")
                        .param("code", PLUGIN_MENU_CODE))
                .andExpect(status().isOk());

        verify(menuAppService).disable(PLUGIN_MENU_CODE);
    }

    @Test
    void updateKeepsLegacyPathEndpointForCodeWithoutSlash() throws Exception {
        when(menuAppService.update(any(MenuUpdateCmd.class))).thenReturn(MenuManageDTO.builder()
                .code(LEGACY_MENU_CODE)
                .name("Legacy menu")
                .type(MenuNodeType.MENU)
                .status(MenuStatus.ACTIVE)
                .build());

        mockMvc.perform(put("/api/system/menus/{code}", LEGACY_MENU_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Legacy menu\",\"type\":\"MENU\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MenuUpdateCmd> commandCaptor = ArgumentCaptor.forClass(MenuUpdateCmd.class);
        verify(menuAppService).update(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getCode()).isEqualTo(LEGACY_MENU_CODE);
    }

    @Test
    void disableKeepsLegacyPathEndpointForCodeWithoutSlash() throws Exception {
        mockMvc.perform(delete("/api/system/menus/{code}", LEGACY_MENU_CODE))
                .andExpect(status().isOk());

        verify(menuAppService).disable(LEGACY_MENU_CODE);
    }
}
