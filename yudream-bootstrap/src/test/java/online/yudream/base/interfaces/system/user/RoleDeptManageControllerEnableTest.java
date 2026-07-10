package online.yudream.base.interfaces.system.user;

import online.yudream.base.application.system.user.service.DeptManageAppService;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.interfaces.system.user.controller.DeptManageController;
import online.yudream.base.interfaces.system.user.controller.RoleManageController;
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
class RoleDeptManageControllerEnableTest {

    @Mock
    private RoleManageAppService roleManageAppService;
    @Mock
    private DeptManageAppService deptManageAppService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new RoleManageController(roleManageAppService),
                new DeptManageController(deptManageAppService)
        ).build();
    }

    @Test
    void enablesRole() throws Exception {
        mockMvc.perform(post("/api/system/roles/{id}/enable", 21L))
                .andExpect(status().isOk());

        verify(roleManageAppService).enable(21L);
    }

    @Test
    void enablesDept() throws Exception {
        mockMvc.perform(post("/api/system/depts/{id}/enable", 11L))
                .andExpect(status().isOk());

        verify(deptManageAppService).enable(11L);
    }
}
