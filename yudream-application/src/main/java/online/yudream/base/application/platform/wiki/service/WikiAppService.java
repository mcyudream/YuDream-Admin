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
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
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
    private final WikiIndexGateway indexGateway;

    @Transactional(readOnly = true)
    public List<WikiSpaceDTO> spaces() { enabled(); return spaceRepo.findAll().stream().map(WikiAssembler::space).toList(); }

    @Transactional
    public WikiSpaceDTO saveSpace(WikiSpaceSaveCmd command) {
        enabled();
        WikiSpace space = command.getId() == null ? WikiSpace.create(command.getName(), command.getSlug()) : space(command.getId());
        spaceRepo.findBySlug(command.getSlug()).filter(item -> !item.getId().equals(space.getId())).ifPresent(item -> { throw new BizException("知识库路径已存在"); });
        space.update(command.getName(), command.getSlug(), command.getDescription(), command.isPublicReadEnabled(), command.isExternalSearchEnabled(), command.getEmbeddingProviderCode(), command.getEmbeddingModelCode(), command.isGraphEnabled(), command.getGraphProviderCode(), command.getGraphModelCode(), command.getChunkSize(), command.getChunkOverlap(), command.getTopK());
        space.setNeo4jConnectionCode(command.getNeo4jConnectionCode() == null ? "" : command.getNeo4jConnectionCode().trim());
        space.setQueryExpansionEnabled(command.isQueryExpansionEnabled());
        space.setRerankEnabled(command.isRerankEnabled());
        return WikiAssembler.space(spaceRepo.save(space));
    }

    @Transactional(readOnly = true)
    public List<WikiNodeDTO> tree(Long spaceId) { enabled(); Map<Long, List<WikiNode>> children = nodeRepo.findBySpaceId(spaceId).stream().collect(Collectors.groupingBy(node -> node.getParentId() == null ? 0L : node.getParentId())); return nodes(children.getOrDefault(0L, List.of()), children, ""); }
    @Transactional public WikiNodeDTO saveNode(WikiNodeSaveCmd c) { enabled(); space(c.getSpaceId()); WikiNode n = c.getId() == null ? createNode(c) : node(c.getId()); if (c.getId() != null) { n.setTitle(c.getTitle()); n.setSlug(c.getSlug()); n.setSort(c.getSort()); if (n.getNodeType() == WikiNodeType.PAGE) n.saveDraft(c.getTitle(), c.getMarkdown()); } return dto(nodeRepo.save(n)); }
    @Transactional public void moveNode(Long id, Long parentId) { enabled(); WikiNode n = node(id); if (parentId != null) { WikiNode p = node(parentId); if (!p.getSpaceId().equals(n.getSpaceId())) throw new BizException("不能跨知识库移动节点"); p.ensureCanContain(n.getNodeType()); n.moveTo(parentId, p.getAncestorPath() + p.getId() + "/"); } else n.moveTo(null, "/"); nodeRepo.save(n); }
    @Transactional public void deleteNode(Long id) { enabled(); WikiNode n = node(id); if (nodeRepo.findBySpaceId(n.getSpaceId()).stream().anyMatch(item -> id.equals(item.getParentId()))) throw new BizException("请先删除目录下的子节点"); if (n.getPublishedVersionId() != null) indexGateway.remove(space(n.getSpaceId()), n.getId(), n.getPublishedVersionId()); nodeRepo.deleteById(id); }
    private WikiNode createNode(WikiNodeSaveCmd c) { if (c.getNodeType() == null) throw new BizException("节点类型不能为空"); if (c.getParentId() != null) { WikiNode p = node(c.getParentId()); if (!p.getSpaceId().equals(c.getSpaceId())) throw new BizException("父节点不属于当前知识库"); p.ensureCanContain(c.getNodeType()); } WikiNode n = c.getNodeType() == WikiNodeType.DIRECTORY ? WikiNode.directory(c.getSpaceId(), c.getParentId(), c.getTitle(), c.getSlug(), c.getSort()) : WikiNode.page(c.getSpaceId(), c.getParentId(), c.getTitle(), c.getSlug(), c.getSort()); if (c.getNodeType() == WikiNodeType.PAGE) n.saveDraft(c.getTitle(), c.getMarkdown()); return n; }
    private List<WikiNodeDTO> nodes(List<WikiNode> source, Map<Long, List<WikiNode>> children, String parentPath) { return source.stream().sorted(Comparator.comparingInt(WikiNode::getSort)).map(n -> { String path = parentPath.isBlank() ? n.getSlug() : parentPath + "/" + n.getSlug(); WikiNodeDTO dto = WikiAssembler.node(n, status(n), nodes(children.getOrDefault(n.getId(), List.of()), children, path)); dto.setPath(path); return dto; }).toList(); }
    private WikiNodeDTO dto(WikiNode node) { return WikiAssembler.node(node, status(node), List.of()); }
    private WikiIndexStatus status(WikiNode node) { return node.getPublishedVersionId() == null ? WikiIndexStatus.DRAFT : versionRepo.findById(node.getPublishedVersionId()).map(WikiPageVersion::getIndexStatus).orElse(WikiIndexStatus.DRAFT); }
    private WikiSpace space(Long id) { return spaceRepo.findById(id).orElseThrow(() -> new BizException("知识库不存在")); }
    private WikiNode node(Long id) { return nodeRepo.findById(id).orElseThrow(() -> new BizException("节点不存在")); }
    private void enabled() { capabilityAppService.ensureEnabled("wiki", "Wiki 知识库"); }
}
