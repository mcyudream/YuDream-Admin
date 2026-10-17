package online.yudream.base.interfaces.system.user;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Validation;
import online.yudream.base.application.system.user.cmd.RolePermissionChangesCmd;
import online.yudream.base.application.system.user.cmd.UserRoleChangesCmd;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.application.system.user.service.RolePermissionChangesAppService;
import online.yudream.base.application.system.user.service.UserRoleChangesAppService;
import online.yudream.base.interfaces.system.user.controller.RoleManageController;
import online.yudream.base.interfaces.system.user.controller.UserManageController;
import online.yudream.base.interfaces.system.user.request.RolePermissionChangesRequest;
import online.yudream.base.interfaces.system.user.request.UserRoleChangesRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PermissionChangesControllerTest {

    @Test
    void roleChangesRequireRoleEditBeforeCallingTheApplication() {
        RolePermissionChangesAppService service = mock(RolePermissionChangesAppService.class);
        RoleManageController controller = new RoleManageController(null, null, service);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.checkPermission("system:role:edit")).thenThrow(new IllegalStateException("无授权权限"));
            assertThrows(IllegalStateException.class, () -> controller.permissionChanges(1L, new RolePermissionChangesRequest()));
            verifyNoInteractions(service);
        }
    }

    @Test
    void roleChangesMapTheExactSnapshotAndReturnTheOriginalRoleResponse() {
        RolePermissionChangesAppService service = mock(RolePermissionChangesAppService.class);
        when(service.apply(any())).thenReturn(RoleManageDTO.builder().id(9007199254740993L).permissions(List.of("unknown", "new")).build());
        RolePermissionChangesRequest request = new RolePermissionChangesRequest();
        request.setExpectedPermissions(List.of("unknown"));
        request.setGrant(List.of("new"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            var response = new RoleManageController(null, null, service).permissionChanges(9007199254740993L, request);
            assertEquals(9007199254740993L, response.getData().getId());
            ArgumentCaptor<RolePermissionChangesCmd> command = ArgumentCaptor.forClass(RolePermissionChangesCmd.class);
            verify(service).apply(command.capture());
            assertEquals(request.getExpectedPermissions(), command.getValue().getExpectedPermissions());
            assertEquals(request.getGrant(), command.getValue().getGrant());
            auth.verify(() -> StpUtil.checkPermission("system:role:edit"));
        }
    }

    @Test
    void userRoleChangesRequireAssignRoleAndPreserveStringIdsInTheCommand() {
        UserRoleChangesAppService service = mock(UserRoleChangesAppService.class);
        UserManageController controller = new UserManageController(null, null, null, service, null);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.checkPermission("system:user:assign-role")).thenThrow(new IllegalStateException("无分配权限"));
            assertThrows(IllegalStateException.class, () -> controller.roleChanges(1L, new UserRoleChangesRequest()));
            verifyNoInteractions(service);
        }
        when(service.apply(any())).thenReturn(UserManageDTO.builder().id(1L).build());
        UserRoleChangesRequest request = new UserRoleChangesRequest();
        request.setExpectedRoleIds(List.of("9007199254740993"));
        request.setGrant(List.of("2"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            controller.roleChanges(1L, request);
            ArgumentCaptor<UserRoleChangesCmd> command = ArgumentCaptor.forClass(UserRoleChangesCmd.class);
            verify(service).apply(command.capture());
            assertEquals(List.of("9007199254740993"), command.getValue().getExpectedRoleIds());
            assertEquals(List.of("2"), command.getValue().getGrant());
            auth.verify(() -> StpUtil.checkPermission("system:user:assign-role"));
            auth.verify(() -> StpUtil.checkPermission("system:user:view"));
        }
    }

    @Test
    void assignRoleWithoutUserViewCannotReadPrivateUserDetailsThroughANoOp() {
        UserRoleChangesAppService service = mock(UserRoleChangesAppService.class);
        UserRoleChangesRequest request = new UserRoleChangesRequest();
        request.setExpectedRoleIds(List.of());
        UserManageController controller = new UserManageController(null, null, null, service, null);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.checkPermission("system:user:view")).thenThrow(new IllegalStateException("无用户查看权限"));
            assertThrows(IllegalStateException.class, () -> controller.roleChanges(1L, request));
            auth.verify(() -> StpUtil.checkPermission("system:user:assign-role"));
            auth.verify(() -> StpUtil.checkPermission("system:user:view"));
            verifyNoInteractions(service);
        }
    }

    @Test
    void snapshotsAreRequiredButEmptySnapshotsAndOmittedChangesAreValid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            RolePermissionChangesRequest role = new RolePermissionChangesRequest();
            UserRoleChangesRequest user = new UserRoleChangesRequest();
            assertFalse(validator.validate(role).isEmpty());
            assertFalse(validator.validate(user).isEmpty());
            role.setExpectedPermissions(List.of());
            user.setExpectedRoleIds(List.of());
            assertTrue(validator.validate(role).isEmpty());
            assertTrue(validator.validate(user).isEmpty());
            role.setGrant(List.of(" "));
            user.setGrant(List.of(" "));
            assertFalse(validator.validate(role).isEmpty());
            assertFalse(validator.validate(user).isEmpty());
        }
    }
}
