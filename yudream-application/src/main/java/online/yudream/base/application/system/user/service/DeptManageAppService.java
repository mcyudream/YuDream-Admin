package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.cmd.DeptCreateCmd;
import online.yudream.base.application.system.user.cmd.DeptUpdateCmd;
import online.yudream.base.application.system.user.dto.DeptManageDTO;
import online.yudream.base.application.system.user.dto.OptionDTO;
import online.yudream.base.application.system.user.query.DeptTreeQuery;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.UserID;
import online.yudream.base.domain.valobj.Phone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeptManageAppService {

    private final DeptRepo deptRepo;
    private final UserRepo userRepo;

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
        Dept dept = Dept.builder()
                .name(cmd.getName())
                .description(cmd.getDescription())
                .leader(toUserID(cmd.getLeaderId()))
                .phone(toPhone(cmd.getPhone()))
                .parentId(toDeptID(cmd.getParentId()))
                .sortOrder(cmd.getSortOrder() == null ? 0 : cmd.getSortOrder())
                .deptType(SystemDeptType.NORMAL)
                .status(DeptStatus.ACTIVE)
                .build();
        return toDTO(deptRepo.save(dept), Map.of());
    }

    @Transactional
    public DeptManageDTO update(DeptUpdateCmd cmd) {
        Dept dept = getDept(cmd.getId());
        ensureParentExists(cmd.getParentId());
        ensureNoParentCycle(dept.getId(), cmd.getParentId());
        ensureNameUnique(dept.getId(), cmd.getName(), cmd.getParentId());
        dept.updateBasic(cmd.getName(), cmd.getDescription(), toUserID(cmd.getLeaderId()), toPhone(cmd.getPhone()), toDeptID(cmd.getParentId()), cmd.getSortOrder());
        if (cmd.getStatus() == DeptStatus.ACTIVE) {
            dept.activate();
        } else if (cmd.getStatus() == DeptStatus.DEPRECATED) {
            ensureDeptCanDisable(dept.getId());
            dept.deactivate();
        }
        return toDTO(deptRepo.save(dept), Map.of());
    }

    @Transactional
    public void disable(Long id) {
        Dept dept = getDept(id);
        ensureDeptCanDisable(id);
        dept.deactivate();
        deptRepo.save(dept);
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
