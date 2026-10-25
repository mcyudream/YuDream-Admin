package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import online.yudream.base.domain.platform.wiki.service.WikiNodePaths;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
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
        space.setGraphTableCode(command.getGraphTableCode() == null ? "" : command.getGraphTableCode().trim());
        space.setQueryExpansionEnabled(command.isQueryExpansionEnabled());
        space.setRerankEnabled(command.isRerankEnabled());
        space.setHitImageLimit(command.getHitImageLimit() == null ? null : Math.clamp(command.getHitImageLimit(), 0, 12));
        space.applyKnowledgeConfig(command.getPurpose(), command.getSchemaContent(), command.getLanguage());
        space.applyModelRouting(command.getChatProviderCode(), command.getChatModelCode(), command.getIngestProviderCode(),
                command.getIngestModelCode(), command.getVisionProviderCode(), command.getVisionModelCode());
        space.applyRerankRouting(command.getRerankProviderCode(), command.getRerankModelCode());
        // API Key 不回显：提交为空白时保留既有值，与前端「留空则不修改」语义一致。
        String webSearchApiKey = StringUtils.hasText(command.getWebSearchApiKey())
                ? command.getWebSearchApiKey() : space.getWebSearchApiKey();
        space.applyWebSearch(command.getWebSearchProviderCode(), webSearchApiKey,
                command.getWebSearchInstanceUrl(), command.getWebSearchEngine());
        space.applyRuntime(command.getContextWindowTokens(), command.isSourceGroundedDefault());
        space.applyWatch(command.isWatchEnabled(), command.getWatchFolderPath());
        return WikiAssembler.space(spaceRepo.save(space));
    }

    @Transactional(readOnly = true)
    public List<WikiNodeDTO> tree(Long spaceId) {
        enabled();
        List<WikiNode> all = nodeRepo.findBySpaceId(spaceId);
        WikiNodePaths paths = new WikiNodePaths(spaceId, all);
        all.forEach(paths::resolve);
        Map<Long, List<WikiNode>> children = all.stream()
                .collect(Collectors.groupingBy(node -> node.getParentId() == null ? 0L : node.getParentId()));
        return nodes(children.getOrDefault(0L, List.of()), children, paths);
    }

    @Transactional(noRollbackFor = BizException.class)
    public WikiNodeDTO saveNode(WikiNodeSaveCmd c) {
        enabled();
        space(c.getSpaceId());
        WikiNode n = c.getId() == null ? createNode(c) : node(c.getId());
        if (!Objects.equals(n.getSpaceId(), c.getSpaceId())) {
            throw new BizException("节点不属于当前知识库");
        }
        List<WikiNode> spaceNodes = new ArrayList<>(nodeRepo.findBySpaceId(c.getSpaceId()));
        if (c.getId() != null && spaceNodes.stream().noneMatch(item -> c.getId().equals(item.getId()))) {
            spaceNodes.add(n);
        }
        if (c.getId() != null) {
            replaceNode(spaceNodes, n);
        }
        WikiNodePaths paths = new WikiNodePaths(c.getSpaceId(), spaceNodes);
        if (c.getParentId() != null && c.getId() == null) {
            WikiNode parent = spaceNodes.stream().filter(item -> c.getParentId().equals(item.getId()))
                    .findFirst().orElseThrow(() -> new BizException("父节点不存在或不属于当前知识库"));
            parent.ensureCanContain(c.getNodeType());
        }
        if (c.getId() != null) {
            paths.resolve(n);
        }
        List<WikiNode> affected = c.getId() == null ? List.of() : subtree(n, spaceNodes);
        Map<Long, String> oldPaths = new HashMap<>();
        for (WikiNode item : affected) {
            oldPaths.put(item.getId(), paths.resolve(item).slugPath());
        }
        if (c.getId() != null) {
            n.setTitle(c.getTitle());
            n.setSlug(c.getSlug());
            n.setSort(c.getSort());
            if (n.getNodeType() == WikiNodeType.PAGE) {
                applyPageContent(n, c);
            }
        }
        n.setAncestorPath(paths.resolve(n).ancestorIds());
        List<WikiNode> changed = new ArrayList<>();
        WikiNodePaths updated = new WikiNodePaths(c.getSpaceId(), spaceNodes);
        for (WikiNode item : affected) {
            String newPath = updated.resolve(item).slugPath();
            if (!Objects.equals(oldPaths.get(item.getId()), newPath)
                    && item.getPublishedVersionId() != null) {
                changed.add(item);
            }
        }
        WikiSpace space = changed.isEmpty() ? null : space(c.getSpaceId());
        WikiNode saved = nodeRepo.save(n);
        syncIndexPaths(changed, space, item -> updated.resolve(item).slugPath());
        return dto(saved);
    }

    @Transactional(noRollbackFor = BizException.class)
    public void moveNode(Long id, Long parentId) {
        enabled();
        WikiNode n = node(id);
        List<WikiNode> spaceNodes = new ArrayList<>(nodeRepo.findBySpaceId(n.getSpaceId()));
        if (spaceNodes.stream().noneMatch(item -> id.equals(item.getId()))) {
            spaceNodes.add(n);
        }
        replaceNode(spaceNodes, n);
        WikiNodePaths paths = new WikiNodePaths(n.getSpaceId(), spaceNodes);
        if (parentId != null) {
            WikiNode parent = spaceNodes.stream().filter(item -> parentId.equals(item.getId())).findFirst()
                    .orElseThrow(() -> new BizException("父节点不存在或不属于当前知识库"));
            parent.ensureCanContain(n.getNodeType());
        }
        List<WikiNode> affected = subtree(n, spaceNodes);
        Map<Long, WikiNodePaths.Paths> targetPaths = new HashMap<>();
        // 任何 Mongo 写入前验证整个受影响子树的父链与深度。
        for (WikiNode item : affected) {
            targetPaths.put(item.getId(), paths.resolve(item, id, parentId));
        }
        WikiSpace space = affected.stream().anyMatch(item -> item.getPublishedVersionId() != null)
                ? space(n.getSpaceId()) : null;
        n.setParentId(parentId);
        for (WikiNode item : affected) {
            item.setAncestorPath(targetPaths.get(item.getId()).ancestorIds());
            nodeRepo.save(item);
        }
        syncIndexPaths(affected.stream().filter(item -> item.getPublishedVersionId() != null).toList(),
                space, item -> targetPaths.get(item.getId()).slugPath());
    }

    private void replaceNode(List<WikiNode> spaceNodes, WikiNode node) {
        for (int index = 0; index < spaceNodes.size(); index++) {
            if (Objects.equals(spaceNodes.get(index).getId(), node.getId())) {
                spaceNodes.set(index, node);
                return;
            }
        }
    }

    private List<WikiNode> subtree(WikiNode root, List<WikiNode> spaceNodes) {
        List<WikiNode> affected = new ArrayList<>(List.of(root));
        java.util.Set<Long> visited = new java.util.HashSet<>();
        visited.add(root.getId());
        for (int index = 0; index < affected.size(); index++) {
            Long parentId = affected.get(index).getId();
            for (WikiNode child : spaceNodes) {
                if (parentId != null && parentId.equals(child.getParentId()) && visited.add(child.getId())) {
                    affected.add(child);
                }
            }
        }
        return affected;
    }

    private void syncIndexPaths(List<WikiNode> affected, WikiSpace space,
                                java.util.function.Function<WikiNode, String> pathFor) {
        if (affected.isEmpty()) {
            return;
        }
        List<String> failures = new ArrayList<>();
        for (WikiNode item : affected) {
            try {
                indexGateway.updatePath(space, item.getId(), item.getPublishedVersionId(), pathFor.apply(item));
            }
            catch (Exception error) {
                String reason = "路径索引同步失败：" + error.getMessage();
                try {
                    WikiPageVersion version = versionRepo.findById(item.getPublishedVersionId())
                            .orElseThrow(() -> new BizException("当前发布版本不存在"));
                    version.failed(reason);
                    versionRepo.save(version);
                }
                catch (Exception recordError) {
                    log.error("Wiki 页面 {} 索引失败状态记录失败，发布版本 {}", item.getId(),
                            item.getPublishedVersionId(), recordError);
                    failures.add(String.valueOf(item.getId()) + "（FAILED 状态记录失败）");
                    continue;
                }
                log.error("Wiki 页面 {} 路径索引同步失败，发布版本 {} 已标记 FAILED", item.getId(),
                        item.getPublishedVersionId(), error);
                failures.add(String.valueOf(item.getId()));
            }
        }
        if (!failures.isEmpty()) {
            throw new BizException("Wiki 结构已保存，但 " + failures.size() + " 个页面路径索引同步失败（页面 ID："
                    + String.join("、", failures) + "）；请重建索引重试");
        }
    }

    @Transactional
    public void deleteNode(Long id) {
        enabled();
        WikiNode n = node(id);
        if (nodeRepo.findBySpaceId(n.getSpaceId()).stream().anyMatch(item -> id.equals(item.getParentId()))) {
            throw new BizException("请先删除目录下的子节点");
        }
        if (n.getPublishedVersionId() != null) {
            // 图数据库不可达时降级：主数据照常删除，残留索引由「重建索引」清空兜底
            removeIndexQuietly(space(n.getSpaceId()), n.getId(), n.getPublishedVersionId());
        }
        nodeRepo.deleteById(id);
    }

    /** 索引清理降级：Neo4j 等图库不可达不应阻断主数据删除；残留索引由重建索引兜底。 */
    private void removeIndexQuietly(WikiSpace space, Long nodeId, Long versionId) {
        try {
            indexGateway.remove(space, nodeId, versionId);
        }
        catch (Exception error) {
            log.warn("Wiki 页面 {} 的图谱索引清理失败（版本 {}），已跳过并保留主数据删除：{}",
                    nodeId, versionId, error.getMessage());
        }
    }

    @Transactional
    public void deleteSpace(Long id) {
        enabled();
        WikiSpace space = space(id);
        // 级联清理：页面检索索引 -> 目录/页面 -> 资料源（含文件对象）-> 摄入任务/审核项 -> 知识库
        for (WikiNode node : nodeRepo.findBySpaceId(id)) {
            if (node.getPublishedVersionId() != null) {
                removeIndexQuietly(space, node.getId(), node.getPublishedVersionId());
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
        WikiNode n = c.getNodeType() == WikiNodeType.DIRECTORY
                ? WikiNode.directory(c.getSpaceId(), c.getParentId(), c.getTitle(), c.getSlug(), c.getSort())
                : WikiNode.page(c.getSpaceId(), c.getParentId(), c.getTitle(), c.getSlug(), c.getSort());
        n.setAncestorPath(new WikiNodePaths(c.getSpaceId(), nodeRepo.findBySpaceId(c.getSpaceId()))
                .resolve(n).ancestorIds());
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

    private List<WikiNodeDTO> nodes(List<WikiNode> source, Map<Long, List<WikiNode>> children, WikiNodePaths paths) {
        return source.stream().sorted(Comparator.comparingInt(WikiNode::getSort)).map(n -> {
            String path = paths.resolve(n).slugPath();
            WikiNodeDTO dto = WikiAssembler.node(n, status(n), nodes(children.getOrDefault(n.getId(), List.of()), children, paths));
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
