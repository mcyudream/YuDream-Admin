package online.yudream.base.interfaces.system.security;

import online.yudream.base.application.system.security.service.OAuthPasskeyAppService;
import online.yudream.base.interfaces.system.security.controller.OAuthPasskeyController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OAuthExplicitEnableControllerTest {

    @Mock
    private OAuthPasskeyAppService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OAuthPasskeyController(service)).build();
    }

    @Test
    void enablesOAuthClient() throws Exception {
        mockMvc.perform(post("/api/system/security/oauth/clients/{id}/enable", 11L))
                .andExpect(status().isOk());

        verify(service).enableClient(11L);
    }

    @Test
    void enablesOAuthProvider() throws Exception {
        mockMvc.perform(post("/api/system/security/oauth/providers/{id}/enable", 12L))
                .andExpect(status().isOk());

        verify(service).enableProvider(12L);
    }
}
