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
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WikiAppService {
    private final CapabilityAppService capabilityAppService;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiPageVersionRepo versionRepo;
    private final WikiSourceRepo sourceRepo;
    private final WikiIngestTaskRepo ingestTaskRepo;
    private final WikiReviewItemRepo reviewItemRepo;
    private final WikiSourceAppService sourceAppService;
    private final WikiIndexGateway indexGateway;

    @Transactional(readOnly = true)
    public List<WikiSpaceDTO> spaces() {
        enabled();
        return spaceRepo.findAll().stream().map(WikiAssembler::space).toList();
    }

    @Transactional(readOnly = true)
    public WikiSpaceDTO spaceDetail(Long id) {
        enabled();
        return WikiAssembler.space(space(id));
    }

    @Transactional
    public WikiSpaceDTO saveSpace(WikiSpaceSaveCmd command) {
        enabled();
        WikiSpace space = command.getId() == null ? WikiSpace.create(command.getName(), command.getSlug()) : space(command.getId());
        spaceRepo.findBySlug(command.getSlug())
                .filter(item -> !item.getId().equals(space.getId()))
                .ifPresent(item -> {
                    throw new BizException("知识库路径已存在");
                });
        space.update(command.getName(), command.getSlug(), command.getDescription(), command.isPublicReadEnabled(),
                command.isExternalSearchEnabled(), command.getEmbeddingProviderCode(), command.getEmbeddingModelCode(),
                command.isGraphEnabled(), command.getGraphProviderCode(), command.getGraphModelCode(),
                command.getChunkSize(), command.getChunkOverlap(), command.getTopK());
        space.setNeo4jConnectionCode(command.getNeo4jConnectionCode() == null ? "" : command.getNeo4jConnectionCode().trim());
        space.setQueryExpansionEnabled(command.isQueryExpansionEnabled());
        space.setRerankEnabled(command.isRerankEnabled());
        space.applyKnowledgeConfig(command.getPurpose(), command.getSchemaContent(), command.getLanguage());
        space.applyModelRouting(command.getChatProviderCode(), command.getChatModelCode(), command.getIngestProviderCode(),
                command.getIngestModelCode(), command.getVisionProviderCode(), command.getVisionModelCode());
        space.applyWebSearch(command.getWebSearchProviderCode(), command.getWebSearchApiKey(),
                command.getWebSearchInstanceUrl(), command.getWebSearchEngine());
        space.applyRuntime(command.getContextWindowTokens(), command.isSourceGroundedDefault());
        space.applyWatch(command.isWatchEnabled(), command.getWatchFolderPath());
        return WikiAssembler.space(spaceRepo.save(space));
    }

    @Transactional(readOnly = true)
    public List<WikiNodeDTO> tree(Long spaceId) {
        enabled();
        Map<Long, List<WikiNode>> children = nodeRepo.findBySpaceId(spaceId).stream()
                .collect(Collectors.groupingBy(node -> node.getParentId() == null ? 0L : node.getParentId()));
        return nodes(children.getOrDefault(0L, List.of()), children, "");
    }

    @Transactional
    public WikiNodeDTO saveNode(WikiNodeSaveCmd c) {
        enabled();
        space(c.getSpaceId());
        WikiNode n = c.getId() == null ? createNode(c) : node(c.getId());
        if (c.getId() != null) {
            n.setTitle(c.getTitle());
            n.setSlug(c.getSlug());
            n.setSort(c.getSort());
            if (n.getNodeType() == WikiNodeType.PAGE) {
                applyPageContent(n, c);
            }
        }
        return dto(nodeRepo.save(n));
    }

    @Transactional
    public void moveNode(Long id, Long parentId) {
        enabled();
        WikiNode n = node(id);
        if (parentId != null) {
            WikiNode p = node(parentId);
            if (!p.getSpaceId().equals(n.getSpaceId())) {
                throw new BizException("不能跨知识库移动节点");
            }
            p.ensureCanContain(n.getNodeType());
            n.moveTo(parentId, p.getAncestorPath() + p.getId() + "/");
        }
        else {
            n.moveTo(null, "/");
        }
        nodeRepo.save(n);
    }

    @Transactional
    public void deleteNode(Long id) {
        enabled();
        WikiNode n = node(id);
        if (nodeRepo.findBySpaceId(n.getSpaceId()).stream().anyMatch(item -> id.equals(item.getParentId()))) {
            throw new BizException("请先删除目录下的子节点");
        }
        if (n.getPublishedVersionId() != null) {
            indexGateway.remove(space(n.getSpaceId()), n.getId(), n.getPublishedVersionId());
        }
        nodeRepo.deleteById(id);
    }

    @Transactional
    public void deleteSpace(Long id) {
        enabled();
        WikiSpace space = space(id);
        // 级联清理：页面检索索引 -> 目录/页面 -> 资料源（含文件对象）-> 摄入任务/审核项 -> 知识库
        for (WikiNode node : nodeRepo.findBySpaceId(id)) {
            if (node.getPublishedVersionId() != null) {
                indexGateway.remove(space, node.getId(), node.getPublishedVersionId());
            }
            nodeRepo.deleteById(node.getId());
        }
        for (WikiSource source : sourceRepo.findBySpaceId(id)) {
            sourceAppService.delete(source.getId());
        }
        // 资料源删除会登记索引清理任务；空间既已整体删除，清理任务一并移除
        ingestTaskRepo.findBySpaceId(id).forEach(task -> ingestTaskRepo.deleteById(task.getId()));
        reviewItemRepo.findBySpaceId(id).forEach(item -> reviewItemRepo.deleteById(item.getId()));
        spaceRepo.deleteById(space.getId());
    }

    private WikiNode createNode(WikiNodeSaveCmd c) {
        if (c.getNodeType() == null) {
            throw new BizException("节点类型不能为空");
        }
        if (c.getParentId() != null) {
            WikiNode p = node(c.getParentId());
            if (!p.getSpaceId().equals(c.getSpaceId())) {
                throw new BizException("父节点不属于当前知识库");
            }
            p.ensureCanContain(c.getNodeType());
        }
        WikiNode n = c.getNodeType() == WikiNodeType.DIRECTORY
                ? WikiNode.directory(c.getSpaceId(), c.getParentId(), c.getTitle(), c.getSlug(), c.getSort())
                : WikiNode.page(c.getSpaceId(), c.getParentId(), c.getTitle(), c.getSlug(), c.getSort());
        if (c.getNodeType() == WikiNodeType.PAGE) {
            applyPageContent(n, c);
        }
        return n;
    }

    private void applyPageContent(WikiNode node, WikiNodeSaveCmd cmd) {
        if (cmd.getBody() != null) {
            WikiPageType pageType = cmd.getPageType() == null
                    ? (node.getPageType() == null ? WikiPageType.CONCEPT : node.getPageType())
                    : cmd.getPageType();
            List<String> sources = cmd.getSources() != null ? cmd.getSources() : node.getSources();
            List<String> related = cmd.getRelated() != null ? cmd.getRelated() : node.getRelated();
            List<String> tags = cmd.getTags() != null ? cmd.getTags() : node.getTags();
            String summary = cmd.getSummary() != null ? cmd.getSummary() : node.getSummary();
            node.savePage(cmd.getTitle(), pageType, sources, related, tags, summary, cmd.getBody());
        }
        else {
            node.saveDraft(cmd.getTitle(), cmd.getMarkdown());
        }
    }

    private List<WikiNodeDTO> nodes(List<WikiNode> source, Map<Long, List<WikiNode>> children, String parentPath) {
        return source.stream().sorted(Comparator.comparingInt(WikiNode::getSort)).map(n -> {
            String path = parentPath.isBlank() ? n.getSlug() : parentPath + "/" + n.getSlug();
            WikiNodeDTO dto = WikiAssembler.node(n, status(n), nodes(children.getOrDefault(n.getId(), List.of()), children, path));
            dto.setPath(path);
            return dto;
        }).toList();
    }

    private WikiNodeDTO dto(WikiNode node) {
        return WikiAssembler.node(node, status(node), List.of());
    }

    private WikiIndexStatus status(WikiNode node) {
        return node.getPublishedVersionId() == null ? WikiIndexStatus.DRAFT
                : versionRepo.findById(node.getPublishedVersionId()).map(WikiPageVersion::getIndexStatus).orElse(WikiIndexStatus.DRAFT);
    }

    private WikiSpace space(Long id) {
        return spaceRepo.findById(id).orElseThrow(() -> new BizException("知识库不存在"));
    }

    private WikiNode node(Long id) {
        return nodeRepo.findById(id).orElseThrow(() -> new BizException("节点不存在"));
    }

    private void enabled() {
        capabilityAppService.ensureEnabled("wiki", "Wiki 知识库");
    }
}
