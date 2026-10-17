package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.assembler.WikiAssembler;
import online.yudream.base.application.platform.wiki.cmd.WikiNodeSaveCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceSaveCmd;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;
import online.yudream.base.domain.platform.wiki.repo.WikiCommentRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceMemberRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiActorGateway;
import online.yudream.base.domain.platform.wiki.service.WikiSpaceAccessResolver;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.platform.wiki.valobj.WikiSpaceStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台侧 Wiki 空间/节点用例入口：先过能力闸门，再校验全局权限码，最后按空间实例 ACL 判定。
 * <p>
 * fail closed：身份缺失、成员缺失、动作不覆盖一律拒绝；LEGACY 空间保持既有全局权限行为。
 * 新建空间先授予创建者 MANAGE；在受限访问覆盖尚未就绪时保持 LEGACY，
 * 就绪后才切换 RESTRICTED，避免留下无人可管或可被旁路的受限空间。
 */
@Service
@RequiredArgsConstructor
public class WikiSpaceAclAppService {

    private final CapabilityAppService capabilityAppService;
    private final WikiSpaceRepo spaceRepo;
    private final WikiSpaceMemberRepo memberRepo;
    private final WikiCommentRepo commentRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiIngestTaskRepo ingestTaskRepo;
    private final WikiActorGateway actorGateway;
    private final WikiAppService wikiAppService;
    private final WikiPublicationAppService publicationAppService;

    /** 能力闸门：每个 Wiki 应用用例都必须先过。 */
    public void ensureEnabled() {
        capabilityAppService.ensureEnabled("wiki", "Wiki 知识库");
    }

    /** 当前认证主体；无法确认时 fail closed。 */
    @Transactional(readOnly = true)
    public WikiActor requireActor() {
        ensureEnabled();
        WikiActor actor = actorGateway.currentActor();
        if (actor == null || !actor.authenticated()) {
            throw new BizException("无法确认当前登录用户，请重新登录后重试");
        }
        return actor;
    }

    /** 全局权限码闸门（实例 ACL 之外的第一道闸门）。 */
    @Transactional(readOnly = true)
    public WikiActor requireGlobalPermission(String permissionCode) {
        WikiActor actor = requireActor();
        if (!actorGateway.hasPermission(permissionCode)) {
            throw new BizException("缺少权限：" + permissionCode);
        }
        return actor;
    }

    /** 全局权限码 + 空间实例 ACL 双闸门。 */
    @Transactional(readOnly = true)
    public WikiAccessGrant requireSpace(Long spaceId, WikiSpaceAclAction action, String permissionCode) {
        WikiActor actor = requireGlobalPermission(permissionCode);
        if (spaceId == null) {
            throw new BizException("知识库不能为空");
        }
        WikiSpace space = requireSpaceEntity(spaceId);
        if (!permits(space, actor, action)) {
            throw new BizException("当前用户没有该知识库的访问权限");
        }
        return new WikiAccessGrant(space, actor);
    }

    /** 由节点反查空间后做双闸门校验（节点不存在或跨库关系一律拒绝）。 */
    @Transactional(readOnly = true)
    public WikiAccessGrant requireNodeSpace(Long nodeId, WikiSpaceAclAction action, String permissionCode) {
        WikiActor actor = requireGlobalPermission(permissionCode);
        if (nodeId == null) {
            throw new BizException("节点不能为空");
        }
        WikiNode node = nodeRepo.findById(nodeId).orElseThrow(() -> new BizException("节点不存在"));
        WikiSpace space = requireSpaceEntity(node.getSpaceId());
        if (!permits(space, actor, action)) {
            throw new BizException("当前用户没有该知识库的访问权限");
        }
        return new WikiAccessGrant(space, actor);
    }

    /** 仅做实例 ACL 判定（调用方已校验全局权限码时使用）。 */
    @Transactional(readOnly = true)
    public WikiSpace requireSpaceAcl(Long spaceId, WikiSpaceAclAction action) {
        WikiActor actor = actorGateway.currentActor();
        WikiSpace space = requireSpaceEntity(spaceId);
        if (!permits(space, actor, action)) {
            throw new BizException("当前用户没有该知识库的访问权限");
        }
        return space;
    }

    /**
     * 以「发起人用户 ID」做实例 ACL 判定：供没有请求级安全上下文的执行线程使用
     * （AI 运行线程里的 wiki 工具）。主体按该用户持久化的部门/角色上下文解析，
     * 用户不存在、未启用或没有有效角色一律按未认证处理并 fail closed。
     */
    @Transactional(readOnly = true)
    public WikiSpace requireSpaceAclForUser(Long actorUserId, Long spaceId, WikiSpaceAclAction action) {
        WikiSpace space = requireSpaceEntity(spaceId);
        WikiActor actor = actorGateway.actorFor(actorUserId);
        if (permits(space, actor, action)) {
            return space;
        }
        if (actor == null || !actor.authenticated()) {
            throw new BizException("无法确认运行发起人的身份，已拒绝访问受限知识库");
        }
        throw new BizException("运行发起人没有该知识库的访问权限");
    }

    /** 加载空间（不含 ACL 判定）；不存在一律拒绝。 */
    @Transactional(readOnly = true)
    public WikiSpace requireSpaceEntity(Long spaceId) {
        if (spaceId == null) {
            throw new BizException("知识库不能为空");
        }
        return spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
    }

    private boolean permits(WikiSpace space, WikiActor actor, WikiSpaceAclAction action) {
        WikiSpaceAclMode mode = space.effectiveAclMode();
        if (mode == WikiSpaceAclMode.LEGACY) {
            return WikiSpaceAccessResolver.permits(mode, List.of(), actor, action);
        }
        List<WikiSpaceMember> members = memberRepo.findBySpaceId(space.getId());
        return WikiSpaceAccessResolver.permits(mode, members, actor, action);
    }

    /** 知识库列表：受限空间只对命中 READ 的成员可见，避免元数据外泄；卡片聚合事实按空间补算。 */
    @Transactional(readOnly = true)
    public List<WikiSpaceDTO> spaces() {
        WikiActor actor = requireGlobalPermission(WikiAccessPermissions.SPACE_VIEW);
        return spaceRepo.findAll().stream()
                .filter(space -> permits(space, actor, WikiSpaceAclAction.READ))
                .map(WikiAssembler::space)
                .map(this::enrichStats)
                .toList();
    }

    @Transactional(readOnly = true)
    public WikiSpaceDTO spaceDetail(Long id) {
        return enrichStats(WikiAssembler.space(requireSpace(id, WikiSpaceAclAction.READ,
                WikiAccessPermissions.SPACE_VIEW).space()));
    }

    /**
     * 列表/详情共用的聚合补充：节点数量、未发布数与最近更新时间，以及失败摄入任务数。
     * 只读投影，不加载正文；入口页卡片不靠额外请求就能显示这些事实。
     */
    private WikiSpaceDTO enrichStats(WikiSpaceDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getId())) {
            return dto;
        }
        Long spaceId = Long.valueOf(dto.getId());
        WikiSpaceStats stats = nodeRepo.statsBySpaceId(spaceId);
        dto.setPageCount(stats.pageCount());
        dto.setDirectoryCount(stats.directoryCount());
        dto.setUnpublishedPageCount(stats.unpublishedPageCount());
        dto.setLastUpdatedAt(stats.lastUpdatedAt());
        dto.setFailedIngestCount((int) ingestTaskRepo.countBySpaceIdAndStatus(spaceId, WikiIngestTaskStatus.FAILED));
        return dto;
    }

    /**
     * 创建/编辑知识库。新建空间授予创建者 MANAGE，覆盖就绪后才启用 RESTRICTED；
     * 编辑既有空间要求实例 MANAGE。
     */
    @Transactional
    public WikiSpaceDTO saveSpace(WikiSpaceSaveCmd command) {
        ensureEnabled();
        WikiActor actor = requireActor();
        if (!actorGateway.hasPermission(WikiAccessPermissions.SPACE_MANAGE)) {
            throw new BizException("缺少权限：" + WikiAccessPermissions.SPACE_MANAGE);
        }
        boolean creating = command.getId() == null;
        if (!creating) {
            requireSpaceAcl(command.getId(), WikiSpaceAclAction.MANAGE);
        }
        WikiSpaceDTO saved = wikiAppService.saveSpace(command);
        Long spaceId = Long.valueOf(saved.getId());
        // 评论开关由本用例维护：评论默认关闭，只有显式开启后公开端才允许发表。
        WikiSpace space = requireSpaceEntity(spaceId);
        space.applyCommentEnabled(command.isCommentEnabled());
        if (creating) {
            // 先授权、再启用 RESTRICTED：任一步失败都不会留下无人可管的受限空间。
            grantManage(spaceId, actor);
            // 覆盖清单不完整时保持 LEGACY（但仍预置创建者 MANAGE），避免出现可绕过的「假受限」。
            if (WikiAccessCoverage.RESTRICTED_EXPOSURE_READY) {
                space.enableRestrictedAcl();
            }
        }
        return enrichStats(WikiAssembler.space(spaceRepo.save(space)));
    }

    /** 级联删除知识库：删除后清理实例 ACL 与评论；不依赖跨集合事务，失败留下的是可清理孤儿数据。 */
    @Transactional
    public void deleteSpace(Long id) {
        requireSpace(id, WikiSpaceAclAction.MANAGE, WikiAccessPermissions.SPACE_DELETE);
        wikiAppService.deleteSpace(id);
        memberRepo.deleteBySpaceId(id);
        commentRepo.deleteBySpaceId(id);
    }

    @Transactional(readOnly = true)
    public List<WikiNodeDTO> tree(Long spaceId) {
        requireSpace(spaceId, WikiSpaceAclAction.READ, WikiAccessPermissions.SPACE_VIEW);
        return wikiAppService.tree(spaceId);
    }

    @Transactional
    public WikiNodeDTO saveNode(WikiNodeSaveCmd command) {
        requireSpace(command.getSpaceId(), WikiSpaceAclAction.EDIT, WikiAccessPermissions.NODE_EDIT);
        return wikiAppService.saveNode(command);
    }

    @Transactional
    public void moveNode(Long nodeId, Long parentId) {
        requireNodeSpace(nodeId, WikiSpaceAclAction.EDIT, WikiAccessPermissions.NODE_EDIT);
        wikiAppService.moveNode(nodeId, parentId);
    }

    @Transactional
    public void deleteNode(Long nodeId) {
        requireNodeSpace(nodeId, WikiSpaceAclAction.EDIT, WikiAccessPermissions.SPACE_DELETE);
        wikiAppService.deleteNode(nodeId);
    }

    @Transactional
    public void publish(Long nodeId) {
        requireNodeSpace(nodeId, WikiSpaceAclAction.EDIT, WikiAccessPermissions.PAGE_PUBLISH);
        publicationAppService.publish(nodeId);
    }

    @Transactional
    public void unpublish(Long nodeId) {
        requireNodeSpace(nodeId, WikiSpaceAclAction.EDIT, WikiAccessPermissions.PAGE_PUBLISH);
        publicationAppService.unpublish(nodeId);
    }

    /** 启用受限 ACL（幂等）。启用前确保存在可管理成员，否则先授予当前操作者 MANAGE。 */
    @Transactional
    public WikiSpaceDTO enableRestrictedAcl(Long spaceId) {
        WikiAccessCoverage.ensureRestrictedExposureReady();
        WikiAccessGrant grant = requireSpace(spaceId, WikiSpaceAclAction.MANAGE,
                WikiAccessPermissions.MEMBER_MANAGE);
        WikiSpace space = grant.space();
        if (space.effectiveAclMode() == WikiSpaceAclMode.RESTRICTED) {
            return WikiAssembler.space(space);
        }
        if (!WikiSpaceAccessResolver.hasManager(memberRepo.findBySpaceId(spaceId))) {
            grantManage(spaceId, grant.actor());
        }
        space.enableRestrictedAcl();
        return WikiAssembler.space(spaceRepo.save(space));
    }

    /** 授予主体 MANAGE（已存在同主体成员时升级动作集合，保留既有动作）。 */
    @Transactional
    public WikiSpaceMember grantManage(Long spaceId, WikiActor actor) {
        WikiSpaceMember existing = memberRepo
                .findBySubject(spaceId, WikiSpaceMemberSubjectType.USER, actor.userId())
                .orElse(null);
        if (existing == null) {
            WikiSpaceMember member = WikiSpaceMember.create(spaceId, WikiSpaceMemberSubjectType.USER, actor.userId(),
                    List.of(WikiSpaceAclAction.MANAGE), actor.userId());
            return memberRepo.save(member);
        }
        if (!existing.manages()) {
            List<WikiSpaceAclAction> actions = new ArrayList<>(existing.getActions());
            actions.add(WikiSpaceAclAction.MANAGE);
            existing.replaceActions(actions, existing.getVersion());
            return memberRepo.save(existing);
        }
        return existing;
    }
}
