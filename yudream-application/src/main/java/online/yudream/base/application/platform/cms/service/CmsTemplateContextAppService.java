package online.yudream.base.application.platform.cms.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.cms.dto.CmsTemplateContextDTO;
import online.yudream.base.application.platform.cms.dto.CmsTemplateItemDTO;
import online.yudream.base.application.platform.cms.query.CmsTemplateContextQuery;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CmsTemplateContextAppService {

    private static final int LATEST_LIMIT = 12;
    private static final int MAX_LIST_LIMIT = 50;
    private static final int CONTENT_LIMIT = 20_000;

    private final CapabilityAppService capabilities;
    private final CmsPageRepo cmsPages;
    private final WikiSpaceRepo wikiSpaces;
    private final WikiNodeRepo wikiNodes;
    private final WikiPageVersionRepo wikiVersions;

    @Transactional(readOnly = true)
    public CmsTemplateContextDTO query() {
        return query(new CmsTemplateContextQuery());
    }

    @Transactional(readOnly = true)
    public CmsTemplateContextDTO query(CmsTemplateContextQuery query) {
        capabilities.ensureEnabled("cms", "CMS 内容");
        CmsTemplateContextQuery safeQuery = query == null ? new CmsTemplateContextQuery() : query;
        int cmsLatestLimit = bounded(safeQuery.getCmsLatestLimit(), LATEST_LIMIT);
        List<CmsTemplateItemDTO> pages = cmsLatestLimit == 0
                ? List.of()
                : cmsPages.publishedPage(null, null, null, 1, cmsLatestLimit)
                .getRecords().stream()
                .map(this::cmsPage)
                .sorted(Comparator.comparing(CmsTemplateItemDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        CmsTemplateContextDTO.CmsTemplateKnowledgeDTO knowledge = knowledge(safeQuery);
        return CmsTemplateContextDTO.builder()
                .cms(CmsTemplateContextDTO.CmsTemplateCmsDTO.builder()
                        .pages(CmsTemplateContextDTO.CmsTemplatePagesDTO.builder()
                                .latest(limit(pages, cmsLatestLimit))
                                .build())
                        .build())
                .knowledge(knowledge)
                .build();
    }

    private CmsTemplateContextDTO.CmsTemplateKnowledgeDTO knowledge(CmsTemplateContextQuery query) {
        if (!capabilities.enabled("wiki")) {
            return CmsTemplateContextDTO.CmsTemplateKnowledgeDTO.builder()
                    .spaces(List.of()).pages(List.of()).latest(List.of()).featured(List.of()).build();
        }
        List<WikiSpace> publicSpaces = wikiSpaces.findAll().stream()
                .filter(WikiSpace::isPublicReadEnabled)
                .toList();
        List<CmsTemplateItemDTO> spaces = publicSpaces.stream().map(this::wikiSpace).toList();
        List<CmsTemplateItemDTO> publishedPages = publicSpaces.stream()
                .flatMap(space -> publishedWikiPages(space).stream())
                .toList();
        List<CmsTemplateItemDTO> pages = publishedPages.stream()
                .sorted(Comparator.comparing(CmsTemplateItemDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<CmsTemplateItemDTO> featured = publishedPages.stream()
                .sorted(Comparator.comparing(CmsTemplateItemDTO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CmsTemplateItemDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CmsTemplateItemDTO::getId))
                .toList();
        int spacesLimit = bounded(query.getKnowledgeSpacesLimit(), MAX_LIST_LIMIT);
        int pagesLimit = bounded(query.getKnowledgePagesLimit(), MAX_LIST_LIMIT);
        int latestLimit = bounded(query.getKnowledgeLatestLimit(), LATEST_LIMIT);
        int featuredLimit = bounded(query.getKnowledgeFeaturedLimit(), LATEST_LIMIT);
        return CmsTemplateContextDTO.CmsTemplateKnowledgeDTO.builder()
                .spaces(limit(spaces, spacesLimit))
                .pages(limit(pages, pagesLimit))
                .latest(limit(pages, latestLimit))
                .featured(limit(featured, featuredLimit))
                .build();
    }

    private List<CmsTemplateItemDTO> publishedWikiPages(WikiSpace space) {
        List<WikiNode> nodes = wikiNodes.findBySpaceId(space.getId());
        Map<Long, WikiNode> byId = nodes.stream()
                .filter(node -> node.getId() != null)
                .collect(Collectors.toMap(WikiNode::getId, Function.identity(), (left, right) -> left));
        return nodes.stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE)
                .filter(node -> node.getPublishedVersionId() != null)
                .sorted(Comparator.comparing(WikiNode::getSort).thenComparing(WikiNode::getId))
                .map(node -> wikiPage(space, node, byId))
                .filter(Objects::nonNull)
                .toList();
    }

    private CmsTemplateItemDTO wikiPage(WikiSpace space, WikiNode node, Map<Long, WikiNode> byId) {
        WikiPageVersion version = wikiVersions.findById(node.getPublishedVersionId()).orElse(null);
        if (version == null) {
            return null;
        }
        String path = wikiPath(node, byId, new HashSet<>());
        String content = limitContent(version.getMarkdown());
        return CmsTemplateItemDTO.builder()
                .id(String.valueOf(node.getId()))
                .sort(node.getSort())
                .source("knowledge")
                .title(version.getTitle() == null ? node.getTitle() : version.getTitle())
                .slug(node.getSlug())
                .summary(content)
                .excerpt(content)
                .url("/wiki/" + space.getSlug() + "/" + path)
                .content(content)
                .markdownContent(content)
                .spaceSlug(space.getSlug())
                .path(path)
                .createdAt(format(version.getCreateTime()))
                .publishedAt(format(version.getCreateTime()))
                .updatedAt(format(version.getUpdateTime() == null ? version.getCreateTime() : version.getUpdateTime()))
                .build();
    }

    private CmsTemplateItemDTO cmsPage(CmsPage page) {
        String html = limitContent(page.getHtmlContent());
        String markdown = limitContent(page.getMarkdownContent());
        String content = html == null || html.isBlank() ? markdown : html;
        return CmsTemplateItemDTO.builder()
                .id(String.valueOf(page.getId()))
                .source("cms")
                .title(page.getTitle())
                .slug(page.getSlug())
                .summary(page.getSummary())
                .excerpt(page.getExcerpt())
                .url("/site/" + page.getSlug())
                .content(content)
                .htmlContent(html)
                .markdownContent(markdown)
                .createdAt(format(page.getCreateTime()))
                .publishedAt(format(page.getPublishedAt()))
                .updatedAt(format(page.getUpdateTime()))
                .build();
    }

    private CmsTemplateItemDTO wikiSpace(WikiSpace space) {
        return CmsTemplateItemDTO.builder()
                .id(String.valueOf(space.getId()))
                .source("knowledge-space")
                .title(space.getName())
                .slug(space.getSlug())
                .summary(space.getDescription())
                .excerpt(space.getDescription())
                .url("/wiki/" + space.getSlug())
                .spaceSlug(space.getSlug())
                .createdAt(format(space.getCreateTime()))
                .updatedAt(format(space.getUpdateTime()))
                .build();
    }

    private String wikiPath(WikiNode node, Map<Long, WikiNode> byId, Set<Long> visited) {
        if (node == null || node.getId() == null || !visited.add(node.getId())) {
            return node == null ? "" : node.getSlug();
        }
        WikiNode parent = node.getParentId() == null ? null : byId.get(node.getParentId());
        String parentPath = parent == null ? "" : wikiPath(parent, byId, visited);
        return parentPath == null || parentPath.isBlank() ? node.getSlug() : parentPath + "/" + node.getSlug();
    }

    private <T> List<T> limit(List<T> values, int limit) {
        int safeLimit = bounded(limit, LATEST_LIMIT);
        return values.size() <= safeLimit ? values : new ArrayList<>(values.subList(0, safeLimit));
    }

    private int bounded(Integer requested, int fallback) {
        if (requested == null) {
            return fallback;
        }
        if (requested <= 0) {
            return 0;
        }
        return Math.min(requested, MAX_LIST_LIMIT);
    }

    private String limitContent(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= CONTENT_LIMIT ? value : value.substring(0, CONTENT_LIMIT);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
