package online.yudream.base.interfaces.system.user;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import online.yudream.base.application.system.user.dto.PermissionOverviewDTO;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.application.system.user.query.PermissionOverviewQuery;
import online.yudream.base.application.system.user.service.PermissionOverviewAppService;
import online.yudream.base.application.system.user.service.UserManageAppService;
import online.yudream.base.interfaces.system.user.assembler.PermissionOverviewWebAssembler;
import online.yudream.base.interfaces.system.user.controller.RoleManageController;
import online.yudream.base.interfaces.system.user.controller.UserManageController;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionOverviewControllerTest {

    @Test
    void overviewRequiresRoleViewBeforeCallingTheApplication() {
        PermissionOverviewAppService service = mock(PermissionOverviewAppService.class);
        RoleManageController controller = new RoleManageController(null, service, null);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.checkPermission("system:role:view")).thenThrow(new IllegalStateException("禁止查看"));
            assertThrows(IllegalStateException.class, () -> controller.permissionOverview(new PermissionOverviewQuery()));
            verifyNoInteractions(service);
        }
    }

    @Test
    void passesIndependentUserAndMenuAccessFlags() {
        PermissionOverviewAppService service = mock(PermissionOverviewAppService.class);
        PermissionOverviewQuery query = new PermissionOverviewQuery();
        when(service.overview(query, true, false)).thenReturn(emptyOverview(true, false));
        RoleManageController controller = new RoleManageController(null, service, null);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.hasPermission("system:user:view")).thenReturn(true);
            auth.when(() -> StpUtil.hasPermission("system:menu:view")).thenReturn(false);
            assertTrue(controller.permissionOverview(query).getData().canViewUsers());
            verify(service).overview(query, true, false);
            auth.verify(() -> StpUtil.checkPermission("system:role:view"));
        }
    }

    @Test
    void userDetailRequiresUserViewAndReusesTheOriginalResponse() {
        UserManageAppService service = mock(UserManageAppService.class);
        UserManageController controller = new UserManageController(service, null, null, null);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.checkPermission("system:user:view")).thenThrow(new IllegalStateException("禁止查看"));
            assertThrows(IllegalStateException.class, () -> controller.get(9007199254740993L));
            verifyNoInteractions(service);
        }
        when(service.get(9007199254740993L)).thenReturn(UserManageDTO.builder().id(9007199254740993L)
                .username("operator").nickname("操作员").roleIds(List.of(1L)).deptIds(List.of(2L)).build());
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            assertEquals(9007199254740993L, controller.get(9007199254740993L).getData().getId());
            verify(service).get(9007199254740993L);
            auth.verify(() -> StpUtil.checkPermission("system:user:view"));
        }
    }

    @Test
    void overviewCountsStayNumericUnderTheGlobalLongIdSerializer() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new SimpleModule()
                .addSerializer(Long.class, ToStringSerializer.instance)
                .addSerializer(Long.TYPE, ToStringSerializer.instance));
        JsonNode root = mapper.readTree(mapper.writeValueAsString(PermissionOverviewWebAssembler.toRes(emptyOverview(true, false))));
        assertTrue(root.get("userTotal").isNumber());
        assertTrue(root.get("stats").get("userCount").isNumber());
        assertTrue(root.get("stats").get("assignmentCount").isNumber());
        assertTrue(root.get("stats").get("menuCount").isNull());
        assertFalse(root.has("email"));
    }

    private PermissionOverviewDTO emptyOverview(boolean users, boolean menus) {
        return new PermissionOverviewDTO(List.of(), List.of(), List.of(), List.of(),
                new PermissionOverviewDTO.StatisticsDTO(10L, 3, null, 1L, 2L, null, 12L),
                1, 30, 10L, users, menus, List.of("3"));
    }
}
