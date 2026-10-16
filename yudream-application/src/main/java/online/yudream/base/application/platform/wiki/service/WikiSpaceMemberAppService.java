package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.assembler.WikiSpaceMemberAssembler;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceMemberSaveCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceMemberUpdateCmd;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceMemberDTO;
import online.yudream.base.application.platform.wiki.query.WikiSpaceMemberPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceMemberRepo;
import online.yudream.base.domain.platform.wiki.service.WikiSpaceAccessResolver;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 空间成员（实例 ACL）管理用例。
 * <p>
 * 每个用例都过能力闸门 + 全局权限码 + 空间 MANAGE；主体必须真实存在；
 * 更新走 CAS；受限空间任何可能导致「无人可管」的变更一律拒绝。
 */
@Service
@RequiredArgsConstructor
public class WikiSpaceMemberAppService {

    private final WikiSpaceMemberRepo memberRepo;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final WikiSpaceAclAppService accessAppService;

    @Transactional(readOnly = true)
    public PageResult<WikiSpaceMemberDTO> page(WikiSpaceMemberPageQuery query) {
        WikiSpace space = accessAppService.requireSpace(query.getSpaceId(), WikiSpaceAclAction.READ,
                WikiAccessPermissions.MEMBER_VIEW).space();
        PageResult<WikiSpaceMember> page = memberRepo.page(space.getId(), query.getPage(), query.getSize());
        return new PageResult<>(WikiSpaceMemberAssembler.toDTOs(page.getRecords(), subjectNames(page.getRecords())),
                page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional
    public WikiSpaceMemberDTO create(WikiSpaceMemberSaveCmd cmd) {
        WikiAccessGrant grant = accessAppService.requireSpace(cmd.getSpaceId(), WikiSpaceAclAction.MANAGE,
                WikiAccessPermissions.MEMBER_MANAGE);
        requireSubjectExists(cmd.getSubjectType(), cmd.getSubjectId());
        memberRepo.findBySubject(cmd.getSpaceId(), cmd.getSubjectType(), cmd.getSubjectId()).ifPresent(existing -> {
            throw new BizException("该主体已授权，请直接调整授权动作");
        });
        WikiSpaceMember member = WikiSpaceMember.create(cmd.getSpaceId(), cmd.getSubjectType(), cmd.getSubjectId(),
                cmd.getActions(), grant.actor().userId());
        WikiSpaceMember saved = memberRepo.save(member);
        return WikiSpaceMemberAssembler.toDTO(saved, subjectName(saved.getSubjectType(), saved.getSubjectId()));
    }

    @Transactional
    public WikiSpaceMemberDTO update(WikiSpaceMemberUpdateCmd cmd) {
        WikiSpace space = accessAppService.requireSpace(cmd.getSpaceId(), WikiSpaceAclAction.MANAGE,
                WikiAccessPermissions.MEMBER_MANAGE).space();
        WikiSpaceMember member = memberInSpace(cmd.getSpaceId(), cmd.getMemberId());
        if (cmd.getExpectedVersion() == null) {
            throw new BizException("成员更新必须携带期望版本");
        }
        requireManagerRemains(space, member, cmd.getActions());
        member.replaceActions(cmd.getActions(), cmd.getExpectedVersion());
        WikiSpaceMember saved = memberRepo.save(member);
        return WikiSpaceMemberAssembler.toDTO(saved, subjectName(saved.getSubjectType(), saved.getSubjectId()));
    }

    @Transactional
    public void delete(Long spaceId, Long memberId) {
        WikiSpace space = accessAppService.requireSpace(spaceId, WikiSpaceAclAction.MANAGE,
                WikiAccessPermissions.MEMBER_DELETE).space();
        WikiSpaceMember member = memberInSpace(spaceId, memberId);
        requireManagerRemains(space, member, List.of());
        memberRepo.deleteById(member.getId());
    }

    /** 受限空间不变量：变更后必须仍存在至少一名持有 MANAGE 的成员，否则拒绝。 */
    private void requireManagerRemains(WikiSpace space, WikiSpaceMember changing, List<WikiSpaceAclAction> actions) {
        if (space.effectiveAclMode() != WikiSpaceAclMode.RESTRICTED) {
            return;
        }
        if (actions != null && actions.contains(WikiSpaceAclAction.MANAGE)) {
            return;
        }
        List<WikiSpaceMember> remaining = new ArrayList<>(memberRepo.findBySpaceId(space.getId()));
        remaining.removeIf(member -> Objects.equals(member.getId(), changing.getId()));
        if (!WikiSpaceAccessResolver.hasManager(remaining)) {
            throw new BizException("受限知识库必须保留至少一名可管理成员");
        }
    }

    private WikiSpaceMember memberInSpace(Long spaceId, Long memberId) {
        if (memberId == null) {
            throw new BizException("成员不能为空");
        }
        WikiSpaceMember member = memberRepo.findById(memberId).orElseThrow(() -> new BizException("成员不存在"));
        if (!Objects.equals(member.getSpaceId(), spaceId)) {
            throw new BizException("成员不属于当前知识库");
        }
        return member;
    }

    /** 主体必须是真实存在的用户或角色；不存在的主体拒绝入库（fail closed）。 */
    private void requireSubjectExists(WikiSpaceMemberSubjectType subjectType, Long subjectId) {
        if (subjectType == null || subjectId == null) {
            throw new BizException("成员主体不能为空");
        }
        boolean exists = switch (subjectType) {
            case USER -> userRepo.findById(subjectId).isPresent();
            case ROLE -> roleRepo.findById(subjectId).isPresent();
        };
        if (!exists) {
            throw new BizException("成员主体不存在");
        }
    }

    private String subjectName(WikiSpaceMemberSubjectType subjectType, Long subjectId) {
        if (subjectType == null || subjectId == null) {
            return "";
        }
        return switch (subjectType) {
            case USER -> userRepo.findById(subjectId).map(this::userName).orElse("");
            case ROLE -> roleRepo.findById(subjectId).map(Role::getName).orElse("");
        };
    }

    private Map<String, String> subjectNames(List<WikiSpaceMember> members) {
        List<Long> userIds = members.stream()
                .filter(member -> member.getSubjectType() == WikiSpaceMemberSubjectType.USER)
                .map(WikiSpaceMember::getSubjectId).distinct().toList();
        List<Long> roleIds = members.stream()
                .filter(member -> member.getSubjectType() == WikiSpaceMemberSubjectType.ROLE)
                .map(WikiSpaceMember::getSubjectId).distinct().toList();
        Map<String, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepo.findByIds(userIds)) {
                names.put(WikiSpaceMemberSubjectType.USER.name() + ":" + user.getId(), userName(user));
            }
        }
        if (!roleIds.isEmpty()) {
            for (Role role : roleRepo.findByIds(roleIds)) {
                names.put(WikiSpaceMemberSubjectType.ROLE.name() + ":" + role.getId(), role.getName());
            }
        }
        return names;
    }

    private String userName(User user) {
        return user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname();
    }
}
