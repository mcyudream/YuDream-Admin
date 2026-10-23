package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.application.system.user.cmd.DeptCreateCmd;
import online.yudream.base.application.system.user.cmd.DeptUpdateCmd;
import online.yudream.base.application.system.user.cmd.TemplateDeptUpsertCmd;
import online.yudream.base.application.system.user.dto.DeptManageDTO;
import online.yudream.base.application.system.user.dto.OptionDTO;
import online.yudream.base.application.system.user.dto.TemplateDeptUpsertResult;
import online.yudream.base.application.system.user.query.DeptTreeQuery;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.UserID;
import online.yudream.base.domain.valobj.Phone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeptManageAppService {

    private final DeptRepo deptRepo;
    private final UserRepo userRepo;
    private final FileAppService fileAppService;

    /**
     * 按「同父同级同名」自愈清理重复部门（导入合并产生的冗余；新建部门时同父同名本就被禁止）。
     * 保守策略：保留用户挂靠最多的一个；冗余副本仅在「无用户挂靠且无子部门」时删除，
     * 否则保留并在日志中提示。返回清理数量。
     */
    public int dedupeByParentAndName() {
        List<Dept> all = deptRepo.findAll();
        Map<String, List<Dept>> groups = new LinkedHashMap<>();
        for (Dept dept : all) {
            String parentKey = dept.getParentId() == null ? "root" : String.valueOf(dept.getParentId().getValue());
            groups.computeIfAbsent(parentKey + "|" + dept.getName(), ignored -> new ArrayList<>()).add(dept);
        }
        int removed = 0;
        for (List<Dept> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            Dept keeper = group.get(0);
            long keeperUsers = -1;
            for (Dept candidate : group) {
                long users = userRepo.countByDeptId(candidate.getId());
                if (users > keeperUsers) {
                    keeper = candidate;
                    keeperUsers = users;
                }
            }
            for (Dept candidate : group) {
                if (candidate.getId().equals(keeper.getId())) {
                    continue;
                }
                long users = userRepo.countByDeptId(candidate.getId());
                if (users > 0 || !deptRepo.findChildren(candidate.getId()).isEmpty()) {
                    log.warn("跳过清理重复部门「{}」（id={}）：仍有用户挂靠或子部门，请人工处理",
                            candidate.getName(), candidate.getId());
                    continue;
                }
                deptRepo.deleteById(candidate.getId());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("重复部门清理完成，共删除 {} 个冗余部门", removed);
        }
        return removed;
    }

    @Transactional(readOnly = true)
    public List<DeptManageDTO> tree(DeptTreeQuery query) {
        List<Dept> depts = deptRepo.tree(query.getKeyword(), query.getParentId(), query.getStatus());
        Map<Long, User> leaderMap = loadLeaders(depts);
        List<DeptManageDTO> nodes = depts.stream().map(dept -> toDTO(dept, leaderMap)).toList();
        return buildTree(nodes);
    }

    @Transactional(readOnly = true)
    public List<OptionDTO> options() {
        return deptRepo.findAll().stream()
                .filter(dept -> dept.getStatus() == DeptStatus.ACTIVE)
                .sorted(Comparator.comparing(Dept::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(dept -> OptionDTO.builder()
                        .id(dept.getId())
                        .label(dept.getName())
                        .value(String.valueOf(dept.getId()))
                        .build())
                .toList();
    }

    @Transactional
    public DeptManageDTO create(DeptCreateCmd cmd) {
        ensureParentExists(cmd.getParentId());
        ensureNameUnique(null, cmd.getName(), cmd.getParentId());
        Dept dept = Dept.create(null, cmd.getName(), cmd.getDescription(), toUserID(cmd.getLeaderId()),
                toPhone(cmd.getPhone()), toDeptID(cmd.getParentId()), cmd.getSortOrder(), cmd.getColor());
        return toDTO(deptRepo.save(dept), Map.of());
    }

    @Transactional
    public DeptManageDTO update(DeptUpdateCmd cmd) {
        Dept dept = getDept(cmd.getId());
        ensureParentExists(cmd.getParentId());
        ensureNoParentCycle(dept.getId(), cmd.getParentId());
        ensureNameUnique(dept.getId(), cmd.getName(), cmd.getParentId());
        dept.updateBasic(cmd.getName(), cmd.getDescription(), toUserID(cmd.getLeaderId()), toPhone(cmd.getPhone()), toDeptID(cmd.getParentId()), cmd.getSortOrder());
        dept.updateColor(cmd.getColor());
        if (cmd.getStatus() == DeptStatus.ACTIVE) {
            dept.activate();
        } else if (cmd.getStatus() == DeptStatus.DEPRECATED) {
            ensureDeptCanDisable(dept.getId());
            dept.deactivate();
        }
        return toDTO(deptRepo.save(dept), Map.of());
    }

    @Transactional
    public DeptManageDTO updateAvatar(Long id, Long operatorId, InputStream inputStream, String originalName, String contentType, long size) {
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase().startsWith("image/")) {
            throw new BizException("部门头像只支持图片文件");
        }
        Dept dept = getDept(id);
        FileObjectDTO file = fileAppService.upload(inputStream, originalName, contentType, size, "dept-avatar", operatorId, true);
        dept.updateAvatar(file.getId());
        return toDTO(deptRepo.save(dept), Map.of());
    }

    @Transactional
    public void disable(Long id) {
        Dept dept = getDept(id);
        ensureDeptCanDisable(id);
        dept.deactivate();
        deptRepo.save(dept);
    }

    @Transactional
    public void enable(Long id) {
        Dept dept = getDept(id);
        dept.activate();
        deptRepo.save(dept);
    }

    /**
     * 模板导入：新建部门。目标是否存在由调用方（模板导入服务）按来源 ID/路径先行判定，
     * 判定不存在后调用本方法；{@code parentId} 必须是已解析的本地父部门 ID（可空表示顶层）。
     * 新建部门一律由服务端生成 ID，不写入模板来源 ID。
     */
    @Transactional
    public TemplateDeptUpsertResult createFromTemplate(TemplateDeptUpsertCmd cmd) {
        if (!StringUtils.hasText(cmd.getName())) {
            throw new BizException("部门名称不能为空");
        }
        ensureParentExists(cmd.getParentId());
        ensureNameUnique(null, cmd.getName(), cmd.getParentId());
        Dept dept = Dept.create(null, cmd.getName(), cmd.getDescription(), toUserID(cmd.getLeaderId()),
                toPhone(cmd.getPhone()), toDeptID(cmd.getParentId()), cmd.getSortOrder(), cmd.getColor());
        if (cmd.getStatus() == DeptStatus.DEPRECATED) {
            dept.deactivate();
        }
        Dept saved = deptRepo.save(dept);
        return TemplateDeptUpsertResult.builder()
                .created(true)
                .deptId(saved.getId())
                .name(saved.getName())
                .parentId(saved.getParentId() == null ? null : saved.getParentId().getValue())
                .build();
    }

    /**
     * 模板导入：更新既有部门。目标匹配（来源 ID 优先、来源路径逐段解析兜底）由调用方完成后传入。
     * <p>
     * 受控规则：
     * - 系统部门与根部门拒绝模板修改；
     * - 白名单 name/description/leaderId/phone/parentId/sortOrder/color/status，
     *   模板行缺省（null/空白）字段保持现状不变；
     *   父部门与主管支持显式指定：{@code parentSpecified}/{@code leaderSpecified} 为 true
     *   （或对应 ID 非空）时按 ID 落库，null 即显式摘除到顶层/显式清空主管；
     * - 名称可变，但沿用「同父同级同名唯一」规则；调整父部门时校验存在性与层级循环；
     * - 停用沿用现有安全规则：存在启用子部门或仍有关联用户不可停用；
     *   已停用部门再次要求停用视为幂等通过。
     */
    @Transactional
    public TemplateDeptUpsertResult applyTemplateUpdate(Long deptId, TemplateDeptUpsertCmd cmd) {
        if (deptId == null) {
            throw new BizException("目标部门不能为空");
        }
        Dept dept = getDept(deptId);
        if (dept.isSystem() || dept.isRoot()) {
            throw new BizException("系统部门或根部门不允许通过模板导入修改");
        }
        if (!StringUtils.hasText(cmd.getName())) {
            throw new BizException("部门名称不能为空");
        }
        boolean parentSpecified = cmd.isParentSpecified() || cmd.getParentId() != null;
        Long parentId = parentSpecified ? cmd.getParentId()
                : (dept.getParentId() == null ? null : dept.getParentId().getValue());
        boolean leaderSpecified = cmd.isLeaderSpecified() || cmd.getLeaderId() != null;
        UserID leader = leaderSpecified ? toUserID(cmd.getLeaderId()) : dept.getLeader();
        ensureParentExists(parentId);
        ensureNoParentCycle(dept.getId(), parentId);
        ensureNameUnique(dept.getId(), cmd.getName(), parentId);
        String description = cmd.isTemplateSnapshot() ? cmd.getDescription()
                : cmd.getDescription() != null ? cmd.getDescription() : dept.getDescription();
        Phone phone = cmd.isTemplateSnapshot() ? toPhone(cmd.getPhone())
                : StringUtils.hasText(cmd.getPhone()) ? Phone.of(cmd.getPhone()) : dept.getPhone();
        Integer sortOrder = cmd.isTemplateSnapshot() ? cmd.getSortOrder()
                : cmd.getSortOrder() != null ? cmd.getSortOrder() : dept.getSortOrder();
        dept.updateBasic(cmd.getName(), description, leader, phone, toDeptID(parentId), sortOrder);
        if (cmd.isTemplateSnapshot() || cmd.getColor() != null) {
            dept.updateColor(cmd.getColor());
        }
        if (cmd.getStatus() == DeptStatus.ACTIVE) {
            dept.activate();
        } else if (cmd.getStatus() == DeptStatus.DEPRECATED && dept.getStatus() != DeptStatus.DEPRECATED) {
            ensureDeptCanDisable(dept.getId());
            dept.deactivate();
        }
        Dept saved = deptRepo.save(dept);
        return TemplateDeptUpsertResult.builder()
                .created(false)
                .deptId(saved.getId())
                .name(saved.getName())
                .parentId(saved.getParentId() == null ? null : saved.getParentId().getValue())
                .build();
    }

    private Dept getDept(Long id) {
        return deptRepo.findById(id).orElseThrow(() -> new BizException("部门不存在"));
    }

    private void ensureParentExists(Long parentId) {
        if (parentId != null && deptRepo.findById(parentId).isEmpty()) {
            throw new BizException("上级部门不存在");
        }
    }

    private void ensureNameUnique(Long excludeId, String name, Long parentId) {
        if (deptRepo.existsByNameAndParentExcludeId(name, parentId, excludeId)) {
            throw new BizException("同级部门名称已存在");
        }
    }

    private void ensureNoParentCycle(Long deptId, Long parentId) {
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null) {
            if (cursor.equals(deptId)) {
                throw new BizException("上级部门不能选择自己或下级部门");
            }
            if (!visited.add(cursor)) {
                throw new BizException("部门层级存在循环");
            }
            Dept parent = deptRepo.findById(cursor).orElse(null);
            cursor = parent == null || parent.getParentId() == null ? null : parent.getParentId().getValue();
        }
    }

    private void ensureDeptCanDisable(Long id) {
        if (deptRepo.countActiveChildren(id) > 0) {
            throw new BizException("部门存在启用的子部门，不能停用");
        }
        if (userRepo.countByDeptId(id) > 0) {
            throw new BizException("部门仍有关联用户，不能停用");
        }
    }

    private Map<Long, User> loadLeaders(List<Dept> depts) {
        List<Long> leaderIds = depts.stream()
                .map(Dept::getLeader)
                .filter(Objects::nonNull)
                .map(UserID::getValue)
                .distinct()
                .toList();
        return userRepo.findByIds(leaderIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<DeptManageDTO> buildTree(List<DeptManageDTO> nodes) {
        Map<Long, DeptManageDTO> nodeMap = nodes.stream().collect(Collectors.toMap(DeptManageDTO::getId, Function.identity()));
        List<DeptManageDTO> roots = new ArrayList<>();
        for (DeptManageDTO node : nodes) {
            if (node.getParentId() != null && nodeMap.containsKey(node.getParentId())) {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<DeptManageDTO> nodes) {
        nodes.sort(Comparator.comparing(DeptManageDTO::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
        nodes.forEach(node -> sortTree(node.getChildren()));
    }

    private DeptManageDTO toDTO(Dept dept, Map<Long, User> leaderMap) {
        User leader = dept.getLeader() == null ? null : leaderMap.get(dept.getLeader().getValue());
        return DeptManageDTO.builder()
                .id(dept.getId())
                .name(dept.getName())
                .description(dept.getDescription())
                .avatar(fileAppService.fileUrl(dept.getAvatarFileId()))
                .color(dept.getColor())
                .leaderId(dept.getLeader() == null ? null : dept.getLeader().getValue())
                .leaderName(leader == null ? null : leader.getNickname())
                .phone(dept.getPhone() == null ? null : dept.getPhone().getValue())
                .parentId(dept.getParentId() == null ? null : dept.getParentId().getValue())
                .sortOrder(dept.getSortOrder())
                .deptType(dept.getDeptType())
                .status(dept.getStatus())
                .systemDept(dept.isSystem() || dept.isRoot())
                .createTime(dept.getCreateTime())
                .updateTime(dept.getUpdateTime())
                .build();
    }

    private DeptID toDeptID(Long deptId) {
        return deptId == null ? null : DeptID.of(deptId);
    }

    private UserID toUserID(Long userId) {
        return userId == null ? null : UserID.of(userId);
    }

    private Phone toPhone(String phone) {
        return StringUtils.hasText(phone) ? Phone.of(phone) : null;
    }
}
