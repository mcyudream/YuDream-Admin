package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.assembler.WikiAssembler;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicDocumentDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicDocumentDetailDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WikiPublicAppService {
    private final WikiSpaceRepo spaces;
    private final WikiPageVersionRepo versions;
    private final WikiSourceRepo sources;
    private final WikiAppService wikiAppService;
    private final WikiSearchAppService searches;
    private final CapabilityAppService capabilities;

    @Transactional(readOnly = true)
    public List<WikiPublicSpaceDTO> spaces() {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        return spaces.findAll().stream()
                .filter(WikiSpace::isPublicReadEnabled)
                .map(space -> new WikiPublicSpaceDTO(space.getName(), space.getSlug(), space.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WikiNodeDTO> tree(String spaceSlug) {
        WikiSpace space = publicSpace(spaceSlug);
        List<WikiNodeDTO> stableTree = wikiAppService.tree(space.getId());
        Map<Long, WikiPageVersion> publishedVersions = loadPublishedVersions(stableTree, space.getId());
        return published(stableTree, publishedVersions);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> search(String spaceSlug, String query) {
        WikiSpace space = publicSpace(spaceSlug);
        return enrich(space, searches.searchForPublicSite(space.getSlug(), query, defaultTopK(space), null, true));
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> searchAll(String query, String spaceSlug) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        List<WikiSpace> publicSpaces = spaces.findAll().stream()
                .filter(WikiSpace::isPublicReadEnabled)
                .filter(space -> spaceSlug == null || spaceSlug.isBlank() || spaceSlug.equals(space.getSlug()))
                .toList();
        if (spaceSlug != null && !spaceSlug.isBlank() && publicSpaces.isEmpty()) {
            throw new BizException("该知识库未开放公开阅读");
        }
        return publicSpaces.stream()
                .flatMap(space -> enrich(space, searches.searchForPublicSite(space.getSlug(), query, defaultTopK(space), null, true)).stream())
                .sorted(Comparator.comparingDouble(WikiSearchHitDTO::getScore).reversed())
                .limit(12)
                .toList();
    }

    private int defaultTopK(WikiSpace space) {
        return Math.clamp(space.getTopK(), 1, 8);
    }

    private List<WikiSearchHitDTO> enrich(WikiSpace space, List<WikiSearchHitDTO> hits) {
        return hits.stream().map(hit -> {
            hit.setSpaceSlug(space.getSlug());
            hit.setSpaceName(space.getName());
            return hit;
        }).toList();
    }

    @Transactional(readOnly = true)
    public void ensurePublicSpace(String spaceSlug) {
        publicSpace(spaceSlug);
    }

    /** 原文档目录：已摄入完成且有正文的原始资料，按目录路径与标题排序。 */
    @Transactional(readOnly = true)
    public List<WikiPublicDocumentDTO> documents(String spaceSlug) {
        WikiSpace space = publicSpace(spaceSlug);
        return sources.findBySpaceId(space.getId()).stream()
                .filter(this::readable)
                .sorted(Comparator.comparing((WikiSource source) -> source.getFolderPath() == null ? "/" : source.getFolderPath())
                        .thenComparing(WikiSource::getSort)
                        .thenComparing(WikiSource::getTitle))
                .map(source -> new WikiPublicDocumentDTO(
                        String.valueOf(source.getId()),
                        source.getTitle(),
                        source.getFolderPath(),
                        source.getKind() == null ? null : source.getKind().name(),
                        source.getFormat() == null ? null : source.getFormat().name()))
                .toList();
    }

    /** 原文档详情：原始正文（Markdown/文本）与已摄取图片。 */
    @Transactional(readOnly = true)
    public WikiPublicDocumentDetailDTO document(String spaceSlug, Long sourceId) {
        WikiSpace space = publicSpace(spaceSlug);
        WikiSource source = sources.findById(sourceId).orElseThrow(() -> new BizException("原文档不存在"));
        if (!space.getId().equals(source.getSpaceId()) || !readable(source)) {
            throw new BizException("原文档不存在");
        }
        List<WikiPublicDocumentDetailDTO.Image> images = (source.getImages() == null ? List.<WikiSourceImage>of() : source.getImages())
                .stream()
                .filter(image -> image.fileObjectId() != null)
                .map(image -> new WikiPublicDocumentDetailDTO.Image(
                        "/api/files/" + image.fileObjectId() + "/content",
                        image.caption(), image.width(), image.height()))
                .toList();
        return new WikiPublicDocumentDetailDTO(
                String.valueOf(source.getId()),
                source.getTitle(),
                source.getFolderPath(),
                source.getKind() == null ? null : source.getKind().name(),
                source.getFormat() == null ? null : source.getFormat().name(),
                source.getExtractedText(),
                images);
    }

    private boolean readable(WikiSource source) {
        return source.getIngestStatus() == WikiIngestStatus.INGESTED && StringUtils.hasText(source.getExtractedText());
    }

    private WikiSpace publicSpace(String slug) {
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isPublicReadEnabled()) throw new BizException("该知识库未开放公开阅读");
        return space;
    }

    private Map<Long, WikiPageVersion> loadPublishedVersions(List<WikiNodeDTO> nodes, Long spaceId) {
        Map<Long, String> ownerByVersionId = new LinkedHashMap<>();
        collectPublishedVersionOwners(nodes, ownerByVersionId);
        Map<Long, WikiPageVersion> result = new LinkedHashMap<>();
        for (WikiPageVersion version : versions.findByIds(ownerByVersionId.keySet())) {
            String ownerId = ownerByVersionId.get(version.getId());
            if (ownerId != null && ownerId.equals(String.valueOf(version.getNodeId())) && spaceId.equals(version.getSpaceId())) {
                result.put(version.getId(), version);
            }
        }
        return result;
    }

    private void collectPublishedVersionOwners(List<WikiNodeDTO> nodes, Map<Long, String> owners) {
        for (WikiNodeDTO node : nodes) {
            if (node.getPublishedVersionId() != null) {
                owners.put(Long.valueOf(node.getPublishedVersionId()), node.getId());
            }
            collectPublishedVersionOwners(node.getChildren(), owners);
        }
    }

    private List<WikiNodeDTO> published(List<WikiNodeDTO> nodes, Map<Long, WikiPageVersion> publishedVersions) {
        return nodes.stream().map(node -> {
            List<WikiNodeDTO> children = published(node.getChildren(), publishedVersions);
            if (node.getNodeType() == WikiNodeType.PAGE && node.getPublishedVersionId() == null) {
                return null;
            }
            if (node.getNodeType() == WikiNodeType.DIRECTORY && children.isEmpty()) {
                return null;
            }
            if (node.getNodeType() == WikiNodeType.DIRECTORY) {
                node.setChildren(children);
                return node;
            }
            WikiPageVersion version = publishedVersions.get(Long.valueOf(node.getPublishedVersionId()));
            if (version == null) {
                return null;
            }
            WikiNodeDTO dto = WikiAssembler.publishedNode(node, version);
            if (dto == null) {
                return null;
            }
            dto.setChildren(children);
            return dto;
        }).filter(Objects::nonNull).toList();
    }
}
