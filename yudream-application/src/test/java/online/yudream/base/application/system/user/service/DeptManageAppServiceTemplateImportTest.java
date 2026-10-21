package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.application.system.user.cmd.TemplateDeptUpsertCmd;
import online.yudream.base.application.system.user.dto.TemplateDeptUpsertResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.UserID;
import online.yudream.base.domain.valobj.Phone;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板导入受控 upsert（部门）单测：验证白名单落地、系统/根部门保护、
 * 父部门校验/层级循环与停用安全规则。
 */
class DeptManageAppServiceTemplateImportTest {

    private static final Long DEPT_ID = 1L;
    private static final Long PARENT_ID = 11L;

    private final DeptRepo deptRepo = mock(DeptRepo.class);
    private final UserRepo userRepo = mock(UserRepo.class);
    private final FileAppService fileAppService = mock(FileAppService.class);

    private DeptManageAppService service() {
        return new DeptManageAppService(deptRepo, userRepo, fileAppService);
    }

    private Dept parentDept() {
        return Dept.builder().id(PARENT_ID).name("研发中心")
                .deptType(SystemDeptType.NORMAL).status(DeptStatus.ACTIVE).build();
    }

    private Dept existingDept() {
        return Dept.builder()
                .id(DEPT_ID)
                .name("后端组")
                .description("旧描述")
                .leader(UserID.of(5L))
                .phone(Phone.fromTrusted("13800001234"))
                .parentId(DeptID.of(PARENT_ID))
                .sortOrder(3)
                .deptType(SystemDeptType.NORMAL)
                .status(DeptStatus.ACTIVE)
                .color("#111111")
                .build();
    }

    private TemplateDeptUpsertCmd createCmd() {
        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setSourceId(777L);
        cmd.setName("后端组");
        cmd.setParentId(PARENT_ID);
        return cmd;
    }

    @Test
    void createFromTemplateUsesServerGeneratedIdAndResolvedParent() {
        when(deptRepo.findById(PARENT_ID)).thenReturn(Optional.of(parentDept()));
        when(deptRepo.existsByNameAndParentExcludeId("后端组", PARENT_ID, null)).thenReturn(false);
        when(deptRepo.save(any(Dept.class))).thenReturn(Dept.builder()
                .id(9001L).name("后端组").parentId(DeptID.of(PARENT_ID))
                .deptType(SystemDeptType.NORMAL).status(DeptStatus.ACTIVE).build());

        TemplateDeptUpsertResult result = service().createFromTemplate(createCmd());

        ArgumentCaptor<Dept> captor = ArgumentCaptor.forClass(Dept.class);
        verify(deptRepo).save(captor.capture());
        Dept saved = captor.getValue();
        assertNull(saved.getId(), "新建部门不得写入模板来源 ID，主键由服务端生成");
        assertEquals("后端组", saved.getName());
        assertEquals(DeptID.of(PARENT_ID), saved.getParentId());
        assertEquals(SystemDeptType.NORMAL, saved.getDeptType(), "模板导入只创建普通部门");
        assertEquals(DeptStatus.ACTIVE, saved.getStatus());
        assertTrue(result.isCreated());
        assertEquals(9001L, result.getDeptId());
        assertEquals(PARENT_ID, result.getParentId());
    }

    @Test
    void createFromTemplateRejectsDuplicateNameUnderSameParentWithoutWrite() {
        when(deptRepo.findById(PARENT_ID)).thenReturn(Optional.of(parentDept()));
        when(deptRepo.existsByNameAndParentExcludeId("后端组", PARENT_ID, null)).thenReturn(true);

        assertTrue(assertThrows(BizException.class, () -> service().createFromTemplate(createCmd()))
                .getMessage().contains("同级部门名称已存在"));
        verify(deptRepo, never()).save(any());
    }

    @Test
    void createFromTemplateMirrorsDeprecatedStatus() {
        when(deptRepo.findById(PARENT_ID)).thenReturn(Optional.of(parentDept()));
        when(deptRepo.existsByNameAndParentExcludeId("后端组", PARENT_ID, null)).thenReturn(false);
        when(deptRepo.save(any(Dept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateDeptUpsertCmd cmd = createCmd();
        cmd.setStatus(DeptStatus.DEPRECATED);
        service().createFromTemplate(cmd);

        ArgumentCaptor<Dept> captor = ArgumentCaptor.forClass(Dept.class);
        verify(deptRepo).save(captor.capture());
        assertEquals(DeptStatus.DEPRECATED, captor.getValue().getStatus(), "新建部门按模板镜像停用状态");
    }

    @Test
    void applyTemplateUpdateRejectsSystemAndRootDeptWithoutWrite() {
        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setName("新名");

        for (SystemDeptType protectedType : new SystemDeptType[]{SystemDeptType.SYSTEM, SystemDeptType.ROOT}) {
            Dept dept = existingDept();
            dept.setDeptType(protectedType);
            when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(dept));
            assertTrue(assertThrows(BizException.class, () -> service().applyTemplateUpdate(DEPT_ID, cmd))
                    .getMessage().contains("系统部门或根部门不允许通过模板导入修改"), () -> "类型=" + protectedType);
        }
        verify(deptRepo, never()).save(any());
    }

    @Test
    void applyTemplateUpdateRejectsMissingTargetWithoutWrite() {
        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setName("新名");

        assertEquals("目标部门不能为空", assertThrows(BizException.class,
                () -> service().applyTemplateUpdate(null, cmd)).getMessage());
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.empty());
        assertEquals("部门不存在", assertThrows(BizException.class,
                () -> service().applyTemplateUpdate(DEPT_ID, cmd)).getMessage());
        verify(deptRepo, never()).save(any());
    }

    @Test
    void applyTemplateUpdateAppliesWhitelistAndKeepsUnspecifiedFields() {
        Dept dept = existingDept();
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(dept));
        when(deptRepo.findById(PARENT_ID)).thenReturn(Optional.of(parentDept()));
        when(deptRepo.existsByNameAndParentExcludeId("研发二组", PARENT_ID, DEPT_ID)).thenReturn(false);
        when(deptRepo.save(any(Dept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setName("研发二组");
        cmd.setSortOrder(9);

        TemplateDeptUpsertResult result = service().applyTemplateUpdate(DEPT_ID, cmd);

        ArgumentCaptor<Dept> captor = ArgumentCaptor.forClass(Dept.class);
        verify(deptRepo).save(captor.capture());
        Dept saved = captor.getValue();
        assertEquals("研发二组", saved.getName());
        assertEquals("旧描述", saved.getDescription(), "模板未提供描述时保持不变");
        assertEquals(UserID.of(5L), saved.getLeader(), "模板未提供主管时保持不变");
        assertEquals("13800001234", saved.getPhone().getValue(), "模板未提供电话时保持不变");
        assertEquals(DeptID.of(PARENT_ID), saved.getParentId(), "模板未提供父部门时保持不变");
        assertEquals(9, saved.getSortOrder(), "提供的排序值落库");
        assertEquals("#111111", saved.getColor(), "模板未提供主色时保持不变");
        assertEquals(DeptStatus.ACTIVE, saved.getStatus());
        assertEquals(DEPT_ID, saved.getId(), "更新不得改动既有主键");
        assertEquals(false, result.isCreated());
        assertEquals(DEPT_ID, result.getDeptId());
        assertEquals(PARENT_ID, result.getParentId());
    }

    @Test
    void applyTemplateUpdateRejectsUnknownParentAndParentCycleWithoutWrite() {
        TemplateDeptUpsertCmd unknownParent = new TemplateDeptUpsertCmd();
        unknownParent.setName("新名");
        unknownParent.setParentId(99L);
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(existingDept()));
        when(deptRepo.findById(99L)).thenReturn(Optional.empty());
        assertTrue(assertThrows(BizException.class, () -> service().applyTemplateUpdate(DEPT_ID, unknownParent))
                .getMessage().contains("上级部门不存在"));

        TemplateDeptUpsertCmd cycle = new TemplateDeptUpsertCmd();
        cycle.setName("新名");
        cycle.setParentId(2L);
        Dept child = Dept.builder().id(2L).name("子部门")
                .deptType(SystemDeptType.NORMAL).status(DeptStatus.ACTIVE)
                .parentId(DeptID.of(DEPT_ID)).build();
        when(deptRepo.findById(2L)).thenReturn(Optional.of(child));
        assertTrue(assertThrows(BizException.class, () -> service().applyTemplateUpdate(DEPT_ID, cycle))
                .getMessage().contains("上级部门不能选择自己或下级部门"));
        verify(deptRepo, never()).save(any());
    }

    @Test
    void applyTemplateUpdateBlocksDisablingWithActiveChildrenOrLinkedUsers() {
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(existingDept()));
        when(deptRepo.findById(PARENT_ID)).thenReturn(Optional.of(parentDept()));
        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setName("新名");
        cmd.setStatus(DeptStatus.DEPRECATED);

        when(deptRepo.countActiveChildren(DEPT_ID)).thenReturn(1L);
        assertTrue(assertThrows(BizException.class, () -> service().applyTemplateUpdate(DEPT_ID, cmd))
                .getMessage().contains("部门存在启用的子部门，不能停用"));

        when(deptRepo.countActiveChildren(DEPT_ID)).thenReturn(0L);
        when(userRepo.countByDeptId(DEPT_ID)).thenReturn(2L);
        assertTrue(assertThrows(BizException.class, () -> service().applyTemplateUpdate(DEPT_ID, cmd))
                .getMessage().contains("部门仍有关联用户，不能停用"));
        verify(deptRepo, never()).save(any());
    }

    @Test
    void applyTemplateUpdateIsIdempotentWhenAlreadyDeprecated() {
        Dept dept = existingDept();
        dept.setStatus(DeptStatus.DEPRECATED);
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(dept));
        when(deptRepo.findById(PARENT_ID)).thenReturn(Optional.of(parentDept()));
        when(deptRepo.countActiveChildren(DEPT_ID)).thenReturn(2L);
        when(userRepo.countByDeptId(DEPT_ID)).thenReturn(3L);
        when(deptRepo.save(any(Dept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setName("新名");
        cmd.setStatus(DeptStatus.DEPRECATED);

        service().applyTemplateUpdate(DEPT_ID, cmd);
        verify(deptRepo).save(any(Dept.class));
    }

    @Test
    void applyTemplateUpdateAcceptsProvidedContactAndStatusFields() {
        Dept dept = existingDept();
        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(dept));
        when(deptRepo.findById(PARENT_ID)).thenReturn(Optional.of(parentDept()));
        when(deptRepo.existsByNameAndParentExcludeId("研发二组", PARENT_ID, DEPT_ID)).thenReturn(false);
        when(deptRepo.save(any(Dept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setName("研发二组");
        cmd.setDescription("新描述");
        cmd.setLeaderId(6L);
        cmd.setPhone("13900005678");
        cmd.setColor("#00FF00");

        service().applyTemplateUpdate(DEPT_ID, cmd);

        ArgumentCaptor<Dept> captor = ArgumentCaptor.forClass(Dept.class);
        verify(deptRepo).save(captor.capture());
        Dept saved = captor.getValue();
        assertEquals("新描述", saved.getDescription());
        assertEquals(UserID.of(6L), saved.getLeader());
        assertEquals("13900005678", saved.getPhone().getValue());
        assertEquals("#00ff00", saved.getColor(), "提供的主色按现有规则规范化");
    }

    @Test
    void applyTemplateUpdateHonorsExplicitParentAndLeaderSpecification() {
        when(deptRepo.save(any(Dept.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<Dept> captor = ArgumentCaptor.forClass(Dept.class);

        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(existingDept()));
        when(deptRepo.existsByNameAndParentExcludeId("顶层组", null, DEPT_ID)).thenReturn(false);
        TemplateDeptUpsertCmd detach = new TemplateDeptUpsertCmd();
        detach.setName("顶层组");
        detach.setParentSpecified(true);
        detach.setLeaderSpecified(true);
        TemplateDeptUpsertResult detachResult = service().applyTemplateUpdate(DEPT_ID, detach);
        verify(deptRepo, times(1)).save(captor.capture());
        Dept detached = captor.getValue();
        assertNull(detached.getParentId(), "parentSpecified=true 且 parentId=null 应显式摘除到顶层");
        assertNull(detached.getLeader(), "leaderSpecified=true 且 leaderId=null 应显式清空主管");
        assertEquals("13800001234", detached.getPhone().getValue(), "未显式指定的字段保持不变");
        assertNull(detachResult.getParentId());

        when(deptRepo.findById(DEPT_ID)).thenReturn(Optional.of(existingDept()));
        when(deptRepo.findById(12L)).thenReturn(Optional.of(Dept.builder().id(12L).name("新父部门")
                .deptType(SystemDeptType.NORMAL).status(DeptStatus.ACTIVE).build()));
        when(deptRepo.existsByNameAndParentExcludeId("换父组", 12L, DEPT_ID)).thenReturn(false);
        TemplateDeptUpsertCmd reparent = new TemplateDeptUpsertCmd();
        reparent.setName("换父组");
        reparent.setParentSpecified(true);
        reparent.setParentId(12L);
        reparent.setLeaderSpecified(true);
        reparent.setLeaderId(6L);
        service().applyTemplateUpdate(DEPT_ID, reparent);
        ArgumentCaptor<Dept> reparentCaptor = ArgumentCaptor.forClass(Dept.class);
        verify(deptRepo, times(2)).save(reparentCaptor.capture());
        Dept reparented = reparentCaptor.getAllValues().get(1);
        assertEquals(DeptID.of(12L), reparented.getParentId(), "parentSpecified=true 时按提供的父部门落库");
        assertEquals(UserID.of(6L), reparented.getLeader(), "leaderSpecified=true 时按提供的主管落库");
    }
}
