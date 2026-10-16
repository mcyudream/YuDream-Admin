package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.assembler.WikiCommentAssembler;
import online.yudream.base.application.platform.wiki.cmd.WikiCommentAnchorInput;
import online.yudream.base.application.platform.wiki.cmd.WikiCommentCreateCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiCommentUpdateCmd;
import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.application.platform.wiki.query.WikiPublicCommentPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiCommentRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiActorGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextAnchor;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextBlockHint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 公开端评论用例：只读已发布节点上的已发布评论。
 * <p>
 * 发表/编辑/删除要求的作者身份只从认证上下文推导；匿名评论未开放（默认关闭），
 * 服务端先校验空间公开阅读 + 评论开关 + 节点已发布，再落库为待审核状态。
 * <p>
 * 文本锚点只接受引用文本与定位提示：服务端按「当前已发布版本」解析并盖章版本/修订/哈希，
 * 引用无法唯一解析时拒绝落库；回复不允许自带锚点，只能继承根评论锚点。
 * 编辑与删除在节点已取消发布后 fail closed（评论保留，恢复发布后可继续维护）。
 */
@Service
@RequiredArgsConstructor
public class WikiCommentAppService {

    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiPageVersionRepo versionRepo;
    private final WikiCommentRepo commentRepo;
    private final WikiActorGateway actorGateway;
    private final WikiSpaceAclAppService accessAppService;

    /** 公开分页：根评论按置顶/时间排序，同页浅线程带回各自已发布回复，并按当前发布版本计算锚点状态。 */
    @Transactional(readOnly = true)
    public PageResult<WikiCommentDTO> page(String spaceSlug, Long nodeId, WikiPublicCommentPageQuery query) {
        WikiSpace space = commentableSpace(spaceSlug);
        WikiPageVersion published = requirePublishedPage(space, nodeId).version();
        PageResult<WikiComment> roots = commentRepo.pagePublishedRoots(space.getId(), nodeId,
                query.getPage(), query.getSize());
        List<Long> rootIds = roots.getRecords().stream().map(WikiComment::getId).toList();
        Map<Long, List<WikiComment>> replies = commentRepo.findPublishedReplies(space.getId(), nodeId, rootIds).stream()
                .filter(reply -> reply.getParentId() != null)
                .collect(Collectors.groupingBy(WikiComment::getParentId));
        List<WikiCommentDTO> records = roots.getRecords().stream()
                .map(root -> WikiCommentAssembler.publicView(root, replies.getOrDefault(root.getId(), List.of()).stream()
                        .map(reply -> WikiCommentAssembler.publicView(reply, List.of(), published))
                        .toList(), published))
                .toList();
        return new PageResult<>(records, roots.getTotal(), roots.getPage(), roots.getSize());
    }

    /** 发表评论：作者身份取认证上下文；默认落入待审核状态；锚点由服务端按当前发布版本定版。 */
    @Transactional
    public WikiCommentDTO create(WikiCommentCreateCmd cmd) {
        WikiSpace space = commentableSpace(cmd.getSpaceSlug());
        PublishedPage page = requirePublishedPage(space, cmd.getNodeId());
        WikiActor actor = requireActor();
        WikiComment comment;
        if (cmd.getParentId() != null) {
            if (cmd.getAnchor() != null) {
                throw new BizException("回复评论不能自定义锚点，锚点继承根评论");
            }
            WikiComment root = requireReplyTarget(space, page.node(), cmd.getParentId());
            comment = WikiComment.reply(space.getId(), page.node().getId(), root.getId(), actor.userId(),
                    actor.displayName(), cmd.getContent(), root.getAnchor());
        }
        else {
            WikiTextAnchor anchor = null;
            if (cmd.getAnchor() != null) {
                anchor = WikiTextAnchor.stamp(draftAnchor(cmd.getAnchor()), page.version().getId(),
                        page.version().getRevision(), page.version().getContentHash(), anchorContent(page.version()));
            }
            comment = WikiComment.create(space.getId(), page.node().getId(), null, actor.userId(),
                    actor.displayName(), cmd.getContent(), anchor);
        }
        return WikiCommentAssembler.publicView(commentRepo.save(comment), List.of(), page.version());
    }

    /** 编辑本人评论：作者校验在领域内完成，服务端不接受请求传入的作者 ID；节点未发布一律拒绝。 */
    @Transactional
    public WikiCommentDTO update(WikiCommentUpdateCmd cmd) {
        WikiSpace space = commentableSpace(cmd.getSpaceSlug());
        WikiComment comment = commentInSpace(space, cmd.getCommentId());
        WikiPageVersion published = requirePublishedPage(space, comment.getNodeId()).version();
        comment.updateContent(requireActor().userId(), cmd.getContent());
        return WikiCommentAssembler.publicView(commentRepo.save(comment), List.of(), published);
    }

    /** 删除本人评论（软删除，可恢复）；节点未发布一律拒绝，避免脱离发布上下文的匿名化操作。 */
    @Transactional
    public void delete(String spaceSlug, Long commentId) {
        WikiSpace space = commentableSpace(spaceSlug);
        WikiComment comment = commentInSpace(space, commentId);
        requirePublishedPage(space, comment.getNodeId());
        comment.deleteByAuthor(requireActor().userId());
        commentRepo.save(comment);
    }

    /** 评论写操作必须登录；身份只取认证上下文。 */
    private WikiActor requireActor() {
        WikiActor actor = actorGateway.currentActor();
        if (actor == null || !actor.authenticated()) {
            throw new BizException("请先登录后再操作评论");
        }
        return actor;
    }

    private WikiSpace commentableSpace(String spaceSlug) {
        accessAppService.ensureEnabled();
        if (spaceSlug == null || spaceSlug.isBlank()) {
            throw new BizException("知识库不存在");
        }
        WikiSpace space = spaceRepo.findBySlug(spaceSlug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isPublicReadEnabled()) {
            throw new BizException("该知识库未开放公开阅读");
        }
        if (!space.isCommentEnabled()) {
            throw new BizException("该知识库未开放评论");
        }
        return space;
    }

    /** 评论页面必须是本空间已发布的页面，否则视为不存在；返回已发布版本供锚点定版使用。 */
    private PublishedPage requirePublishedPage(WikiSpace space, Long nodeId) {
        if (nodeId == null) {
            throw new BizException("评论页面不存在");
        }
        WikiNode node = nodeRepo.findById(nodeId).orElseThrow(() -> new BizException("评论页面不存在"));
        if (!Objects.equals(space.getId(), node.getSpaceId()) || node.getNodeType() != WikiNodeType.PAGE
                || node.getPublishedVersionId() == null) {
            throw new BizException("评论页面不存在或未发布");
        }
        WikiPageVersion version = versionRepo.findById(node.getPublishedVersionId())
                .orElseThrow(() -> new BizException("评论页面不存在或未发布"));
        if (!Objects.equals(version.getNodeId(), node.getId()) || !Objects.equals(version.getSpaceId(), space.getId())) {
            throw new BizException("评论页面不存在或未发布");
        }
        return new PublishedPage(node, version);
    }

    /** 同页浅线程：只允许回复本页已发布的根评论。 */
    private WikiComment requireReplyTarget(WikiSpace space, WikiNode node, Long parentId) {
        WikiComment parent = commentRepo.findById(parentId).orElseThrow(() -> new BizException("回复的评论不存在"));
        if (!Objects.equals(parent.getSpaceId(), space.getId()) || !Objects.equals(parent.getNodeId(), node.getId())) {
            throw new BizException("只能回复本页评论");
        }
        if (parent.getParentId() != null) {
            throw new BizException("回复不再嵌套，请回复根评论");
        }
        if (!parent.published()) {
            throw new BizException("只能回复已发布的评论");
        }
        return parent;
    }

    /** 客户端锚点草稿：只映射引用文本与定位提示，版本信息由 stamp 阶段盖章。 */
    private WikiTextAnchor draftAnchor(WikiCommentAnchorInput input) {
        return WikiTextAnchor.draft(input.getExact(), input.getPrefix(), input.getSuffix(),
                blockHint(input), input.getStartOffset(), input.getEndOffset());
    }

    /**
     * 块提示是可选线索：块类型与块序号必须成对出现；表格行列只允许随表格单元格出现。
     * 只提交起止偏移（不带块提示）同样合法。
     */
    private WikiTextBlockHint blockHint(WikiCommentAnchorInput input) {
        boolean hasType = input.getBlockType() != null && !input.getBlockType().isBlank();
        boolean hasOrdinal = input.getBlockOrdinal() != null;
        if (!hasType && !hasOrdinal) {
            if (input.getTableRow() != null || input.getTableCell() != null) {
                throw new BizException("表格锚点必须提供块序号与块类型");
            }
            return null;
        }
        if (!hasType || !hasOrdinal) {
            throw new BizException("引用块提示不完整，请同时提供块类型与块序号");
        }
        return WikiTextBlockHint.of(input.getBlockType(), input.getBlockOrdinal(),
                input.getTableRow(), input.getTableCell());
    }

    /** 锚点解析的正文基准：已发布版本的正文部分（frontmatter 只用于元数据，不参与引用匹配）。 */
    private String anchorContent(WikiPageVersion version) {
        return WikiFrontmatter.parse(version.getMarkdown()).bodyOnly();
    }

    private WikiComment commentInSpace(WikiSpace space, Long commentId) {
        if (commentId == null) {
            throw new BizException("评论不存在");
        }
        WikiComment comment = commentRepo.findById(commentId).orElseThrow(() -> new BizException("评论不存在"));
        if (!Objects.equals(comment.getSpaceId(), space.getId())) {
            throw new BizException("评论不存在");
        }
        return comment;
    }

    /** 评论所在页面的已发布上下文（节点 + 当前已发布版本）。 */
    private record PublishedPage(WikiNode node, WikiPageVersion version) {
    }
}
