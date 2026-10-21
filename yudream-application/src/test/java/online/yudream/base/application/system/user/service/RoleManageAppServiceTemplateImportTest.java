package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.user.cmd.TemplateRoleUpsertCmd;
import online.yudream.base.application.system.user.dto.TemplateRoleUpsertResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板导入受控 upsert（角色）单测：验证白名单落地、不可变/受保护字段与停用安全规则。
 */
class RoleManageAppServiceTemplateImportTest {

    private static final Long ROLE_ID = 9007199254740993L;
    private static final Long DEPT_ID = 11L;

    private final RoleRepo roleRepo = mock(RoleRepo.class);
    private final DeptRepo deptRepo = mock(DeptRepo.class);
    private final PermissionRepo permissionRepo = mock(PermissionRepo.class);
    private final UserRepo userRepo = mock(UserRepo.class);

    private RoleManageAppService service() {
        return new RoleManageAppService(roleRepo, deptRepo, permissionRepo, userRepo);
    }

    private Dept existingDept() {
        return Dept.builder().id(DEPT_ID).name("研发中心").build();
    }

    private Role existingRole() {
        return existingRole(RoleStatus.ACTIVE);
    }

    private Role existingRole(RoleStatus status) {
        return Role.builder()
                .id(ROLE_ID)
                .name("原名称")
                .code("legacy")
                .deptId(DeptID.of(DEPT_ID))
                .level(RoleLevel.ADMIN)
                .systemRole(false)
                .icon("morph:old")
                .color("#111111")
                .permissions(new java.util.ArrayList<>(List.of(PermissionID.of("a"), PermissionID.of("b"))))
                .status(status)
                .build();
    }

    private TemplateRoleUpsertCmd createCmd() {
        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setSourceId(777L);
        cmd.setName("运营");
        cmd.setCode("ops");
        cmd.setDeptId(DEPT_ID);
        cmd.setPermissions(new java.util.ArrayList<>(List.of("system:user:view")));
        return cmd;
    }

    @Test
    void createFromTemplateUsesServerGeneratedIdAndResolvedDept() {
        when(roleRepo.existsByCodeExcludeId("ops", null)).thenReturn(false);
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(existingDept()));
        when(roleRepo.save(any(Role.class))).thenReturn(Role.builder()
                .id(9001L).name("运营").code("ops")
                .deptId(DeptID.of(DEPT_ID)).level(RoleLevel.USER)
                .status(RoleStatus.ACTIVE)
                .permissions(List.of(PermissionID.of("system:user:view")))
                .build());

        TemplateRoleUpsertResult result = service().createFromTemplate(createCmd());

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepo).save(captor.capture());
        Role saved = captor.getValue();
        assertNull(saved.getId(), "新建角色不得写入模板来源 ID，主键由服务端生成");
        assertEquals("运营", saved.getName());
        assertEquals("ops", saved.getCode());
        assertEquals(DeptID.of(DEPT_ID), saved.getDeptId());
        assertEquals(RoleLevel.USER, saved.getLevel(), "缺省权重按现有规则取 USER");
        assertEquals(List.of(PermissionID.of("system:user:view")), saved.getPermissions());
        assertEquals(RoleStatus.ACTIVE, saved.getStatus());
        assertTrue(result.isCreated());
        assertEquals(9001L, result.getRoleId());
        assertEquals("ops", result.getCode());
        assertEquals("运营", result.getName());
    }

    @Test
    void createFromTemplateRejectsDuplicateCodeWithoutWrite() {
        when(roleRepo.existsByCodeExcludeId("ops", null)).thenReturn(true);

        BizException exception = assertThrows(BizException.class, () -> service().createFromTemplate(createCmd()));
        assertEquals("角色编码已存在", exception.getMessage());
        verify(roleRepo, never()).save(any());
    }

    @Test
    void createFromTemplateRejectsMissingDeptWithoutWrite() {
        TemplateRoleUpsertCmd missing = createCmd();
        missing.setDeptId(null);
        assertEquals("部门不能为空", assertThrows(BizException.class, () -> service().createFromTemplate(missing)).getMessage());

        TemplateRoleUpsertCmd unknown = createCmd();
        unknown.setDeptId(99L);
        when(deptRepo.findById(99L)).thenReturn(Optional.empty());
        assertEquals("部门不存在", assertThrows(BizException.class, () -> service().createFromTemplate(unknown)).getMessage());
        verify(roleRepo, never()).save(any());
    }

    @Test
    void createFromTemplateMirrorsDeprecatedStatus() {
        when(roleRepo.existsByCodeExcludeId("ops", null)).thenReturn(false);
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(existingDept()));
        when(roleRepo.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateRoleUpsertCmd cmd = createCmd();
        cmd.setStatus(RoleStatus.DEPRECATED);
        service().createFromTemplate(cmd);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepo).save(captor.capture());
        assertEquals(RoleStatus.DEPRECATED, captor.getValue().getStatus(), "新建角色按模板镜像停用状态");
    }

    @Test
    void applyTemplateUpdateRejectsSystemRoleAndMissingTargetWithoutWrite() {
        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setName("新名");

        assertEquals("目标角色不能为空", assertThrows(BizException.class,
                () -> service().applyTemplateUpdate(null, cmd)).getMessage());
        when(roleRepo.findById(ROLE_ID)).thenReturn(Optional.empty());
        assertEquals("角色不存在", assertThrows(BizException.class,
                () -> service().applyTemplateUpdate(ROLE_ID, cmd)).getMessage());

        Role systemRole = Role.builder()
                .id(ROLE_ID)
                .name("管理员")
                .code("admin")
                .deptId(DeptID.of(DEPT_ID))
                .level(RoleLevel.ADMIN)
                .systemRole(true)
                .systemType(SystemRoleType.ADMIN)
                .status(RoleStatus.ACTIVE)
                .build();
        when(roleRepo.findById(ROLE_ID)).thenReturn(Optional.of(systemRole));
        assertTrue(assertThrows(BizException.class, () -> service().applyTemplateUpdate(ROLE_ID, cmd))
                .getMessage().contains("系统角色不允许通过模板导入修改"));
        verify(roleRepo, never()).save(any());
    }

    @Test
    void applyTemplateUpdateRejectsImmutableCodeConflictWithoutWrite() {
        Role role = existingRole();
        when(roleRepo.findById(ROLE_ID)).thenReturn(Optional.of(role));
        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setName("新名");
        cmd.setCode("ops");

        BizException exception = assertThrows(BizException.class, () -> service().applyTemplateUpdate(ROLE_ID, cmd));
        assertTrue(exception.getMessage().contains("角色编码不可修改"));
        assertTrue(exception.getMessage().contains("ops"));
        assertTrue(exception.getMessage().contains("legacy"));
        verify(roleRepo, never()).save(any());
    }

    @Test
    void applyTemplateUpdateAppliesWhitelistAndKeepsUnspecifiedFields() {
        Role role = existingRole();
        when(roleRepo.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(existingDept()));
        when(roleRepo.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setName("新名");
        cmd.setColor("#00FF00");
        cmd.setPermissions(List.of("system:user:view"));

        TemplateRoleUpsertResult result = service().applyTemplateUpdate(ROLE_ID, cmd);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepo).save(captor.capture());
        Role saved = captor.getValue();
        assertEquals("新名", saved.getName());
        assertEquals("legacy", saved.getCode(), "编码不可变：模板未提供时保持原编码");
        assertEquals(DeptID.of(DEPT_ID), saved.getDeptId(), "模板未提供部门时保持不变");
        assertEquals(RoleLevel.ADMIN, saved.getLevel(), "模板未提供权重时保持不变");
        assertEquals("morph:old", saved.getIcon(), "模板未提供图标时保持不变");
        assertEquals("#00ff00", saved.getColor(), "提供的代表色按现有规则规范化");
        assertEquals(List.of(PermissionID.of("system:user:view")), saved.getPermissions());
        assertEquals(RoleStatus.ACTIVE, saved.getStatus());
        assertEquals(ROLE_ID, saved.getId(), "更新不得改动既有主键");
        assertEquals(false, result.isCreated());
        assertEquals(ROLE_ID, result.getRoleId());
        assertEquals("legacy", result.getCode());
    }

    @Test
    void applyTemplateUpdateReplacesPermissionsWithAnExplicitEmptyTemplateSnapshot() {
        Role role = existingRole();
        when(roleRepo.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepo.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setName("新名");
        cmd.setTemplateSnapshot(true);
        cmd.setPermissions(List.of());

        service().applyTemplateUpdate(ROLE_ID, cmd);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepo).save(captor.capture());
        assertEquals(List.of(), captor.getValue().getPermissions(), "空权限列表示导出的授权快照已被清空");
    }

    @Test
    void applyTemplateUpdateBlocksDisablingWhenRoleStillInUse() {
        Role role = existingRole();
        when(roleRepo.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepo.countByRoleId(ROLE_ID)).thenReturn(2L);
        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setName("新名");
        cmd.setStatus(RoleStatus.DEPRECATED);

        assertTrue(assertThrows(BizException.class, () -> service().applyTemplateUpdate(ROLE_ID, cmd))
                .getMessage().contains("角色仍被用户使用，不能停用"));
        verify(roleRepo, never()).save(any());
    }

    @Test
    void applyTemplateUpdateIsIdempotentWhenAlreadyDeprecated() {
        Role role = existingRole(RoleStatus.DEPRECATED);
        when(roleRepo.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepo.countByRoleId(ROLE_ID)).thenReturn(2L);
        when(roleRepo.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setName("新名");
        cmd.setStatus(RoleStatus.DEPRECATED);

        service().applyTemplateUpdate(ROLE_ID, cmd);
        verify(roleRepo).save(any(Role.class));
    }

    @Test
    void applyTemplateUpdateAcceptsMatchingCodeAndRevalidatesDept() {
        Role role = existingRole();
        when(roleRepo.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(deptRepo.findById(eq(12L))).thenReturn(Optional.of(Dept.builder().id(12L).name("新部门").build()));
        when(roleRepo.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setName("新名");
        cmd.setCode("legacy");
        cmd.setDeptId(12L);

        service().applyTemplateUpdate(ROLE_ID, cmd);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepo).save(captor.capture());
        assertEquals(DeptID.of(12L), captor.getValue().getDeptId(), "提供的部门 ID 按本地目标落库");
        assertEquals("legacy", captor.getValue().getCode());
    }
}
