package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiExtractionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiSlug;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目迁移：完整归档导出/导入，以及从现有页面重建索引。
 */
@Service
@RequiredArgsConstructor
public class WikiMigrationAppService {

    private final CapabilityAppService capabilities;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiSourceRepo sourceRepo;
    private final WikiIngestAppService ingestAppService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public String exportArchive(Long spaceId) {
        enabled();
        WikiSpace space = spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
        var root = objectMapper.createObjectNode();
        var spaceNode = root.putObject("space");
        spaceNode.put("name", space.getName());
        spaceNode.put("slug", space.getSlug());
        spaceNode.put("description", space.getDescription());
        spaceNode.put("purpose", space.getPurpose());
        spaceNode.put("schemaContent", space.getSchemaContent());
        spaceNode.put("language", space.getLanguage());
        var nodes = root.putArray("nodes");
        for (WikiNode node : nodeRepo.findBySpaceId(spaceId)) {
            var item = nodes.addObject();
            item.put("id", String.valueOf(node.getId()));
            item.put("title", node.getTitle());
            item.put("slug", node.getSlug());
            item.put("nodeType", node.getNodeType() == null ? null : node.getNodeType().name());
            item.put("pageType", node.getPageType() == null ? null : node.getPageType().name());
            item.put("parentId", node.getParentId() == null ? null : String.valueOf(node.getParentId()));
            item.put("sort", node.getSort());
            item.put("markdown", node.getMarkdownDraft());
        }
        var sources = root.putArray("sources");
        for (WikiSource source : sourceRepo.findBySpaceId(spaceId)) {
            var item = sources.addObject();
            item.put("folderPath", source.getFolderPath());
            item.put("fileName", source.getFileName());
            item.put("title", source.getTitle());
            item.put("kind", source.getKind() == null ? null : source.getKind().name());
            item.put("url", source.getUrl());
            item.put("mimeType", source.getMimeType());
            item.put("format", source.getFormat() == null ? null : source.getFormat().name());
            item.put("extractedText", source.getExtractedText());
        }
        try {
            return objectMapper.writeValueAsString(root);
        }
        catch (Exception exception) {
            throw new BizException("导出失败：" + exception.getMessage());
        }
    }

    @Transactional
    public String importArchive(String json) {
        enabled();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode spaceNode = root.path("space");
            String name = spaceNode.path("name").asText("导入的知识库");
            String slug = uniqueSlug(spaceNode.path("slug").asText(WikiSlug.derive(name)));
            WikiSpace space = WikiSpace.create(name, slug);
            space.applyKnowledgeConfig(spaceNode.path("purpose").asText(""),
                    spaceNode.path("schemaContent").asText(""), spaceNode.path("language").asText("zh-CN"));
            space.setDescription(spaceNode.path("description").asText(""));
            WikiSpace saved = spaceRepo.save(space);

            Map<String, JsonNode> nodesById = archiveNodes(root.path("nodes"));
            Map<String, Long> importedIds = new HashMap<>();
            Set<String> visiting = new HashSet<>();
            for (String nodeId : nodesById.keySet()) {
                importNode(nodeId, nodesById, importedIds, visiting, saved.getId());
            }
            root.path("sources").forEach(source -> {
                WikiSource wikiSource = WikiSource.file(saved.getId(), source.path("folderPath").asText("/"),
                        source.path("fileName").asText(""), source.path("title").asText("资料"),
                        source.path("mimeType").asText(""), null, "");
                wikiSource.setKind("URL".equals(source.path("kind").asText())
                        ? online.yudream.base.domain.platform.wiki.enumerate.WikiSourceKind.URL
                        : online.yudream.base.domain.platform.wiki.enumerate.WikiSourceKind.FILE);
                wikiSource.setUrl(source.path("url").asText(""));
                wikiSource.markExtracted(source.path("extractedText").asText(""), List.of());
                wikiSource.skipExtraction("跨设备迁移：原始文件未随归档传输");
                wikiSource.skipIngest("跨设备迁移：Wiki 页面已随归档导入");
                sourceRepo.save(wikiSource);
            });
            return String.valueOf(saved.getId());
        }
        catch (BizException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new BizException("导入失败：" + exception.getMessage());
        }
    }

    @Transactional
    public void rebuildIndex(Long spaceId) {
        enabled();
        spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
        ingestAppService.enqueueReindex(spaceId);
    }

    private Map<String, JsonNode> archiveNodes(JsonNode nodes) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        int legacyIndex = 0;
        for (JsonNode node : nodes) {
            String id = node.path("id").asText("").trim();
            if (id.isBlank()) {
                id = "legacy-" + legacyIndex;
            }
            if (result.putIfAbsent(id, node) != null) {
                throw new BizException("导入归档包含重复节点 ID：" + id);
            }
            legacyIndex++;
        }
        return result;
    }

    private Long importNode(String oldId, Map<String, JsonNode> nodesById, Map<String, Long> importedIds,
                            Set<String> visiting, Long spaceId) {
        Long imported = importedIds.get(oldId);
        if (imported != null) {
            return imported;
        }
        JsonNode node = nodesById.get(oldId);
        if (node == null) {
            throw new BizException("导入归档引用了不存在的父节点：" + oldId);
        }
        if (!visiting.add(oldId)) {
            throw new BizException("导入归档的节点层级存在循环：" + oldId);
        }
        String parentId = node.path("parentId").asText("").trim();
        Long newParentId = parentId.isBlank() || "0".equals(parentId)
                ? null
                : importNode(parentId, nodesById, importedIds, visiting, spaceId);
        WikiNode parent = newParentId == null ? null : nodeRepo.findById(newParentId)
                .orElseThrow(() -> new BizException("导入父节点不存在"));
        String title = node.path("title").asText("未命名页面");
        String slug = node.path("slug").asText(WikiSlug.derive(title));
        WikiNode wikiNode = "DIRECTORY".equals(node.path("nodeType").asText())
                ? WikiNode.directory(spaceId, newParentId, title, slug, node.path("sort").asInt(0))
                : WikiNode.page(spaceId, newParentId, title, slug, node.path("sort").asInt(0));
        if (parent != null) {
            parent.ensureCanContain(wikiNode.getNodeType());
            wikiNode.moveTo(newParentId, parent.getAncestorPath() + parent.getId() + "/");
        }
        String markdown = node.path("markdown").asText("");
        if (wikiNode.getNodeType() == WikiNodeType.PAGE && !markdown.isBlank()) {
            wikiNode.applyGeneratedMarkdown(markdown);
        }
        WikiNode saved = nodeRepo.save(wikiNode);
        importedIds.put(oldId, saved.getId());
        visiting.remove(oldId);
        return saved.getId();
    }

    private String uniqueSlug(String slug) {
        String base = WikiSlug.derive(slug);
        String candidate = base;
        int index = 1;
        while (spaceRepo.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + (index++);
        }
        return candidate;
    }

    private void enabled() {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
    }
}
