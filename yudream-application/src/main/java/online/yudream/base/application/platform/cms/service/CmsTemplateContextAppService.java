package online.yudream.base.application.platform.cms.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.cms.dto.CmsTemplateContextDTO;
import online.yudream.base.application.platform.cms.dto.CmsTemplateItemDTO;
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

    private static final int PAGE_LIMIT = 50;
    private static final int LATEST_LIMIT = 12;
    private static final int CONTENT_LIMIT = 20_000;

    private final CapabilityAppService capabilities;
    private final CmsPageRepo cmsPages;
    private final WikiSpaceRepo wikiSpaces;
    private final WikiNodeRepo wikiNodes;
    private final WikiPageVersionRepo wikiVersions;

    @Transactional(readOnly = true)
    public CmsTemplateContextDTO query() {
        capabilities.ensureEnabled("cms", "CMS 内容");
        List<CmsTemplateItemDTO> pages = cmsPages.publishedPage(null, null, null, 1, PAGE_LIMIT)
                .getRecords().stream()
                .map(this::cmsPage)
                .sorted(Comparator.comparing(CmsTemplateItemDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        CmsTemplateContextDTO.CmsTemplateKnowledgeDTO knowledge = knowledge();
        return CmsTemplateContextDTO.builder()
                .cms(CmsTemplateContextDTO.CmsTemplateCmsDTO.builder()
                        .pages(CmsTemplateContextDTO.CmsTemplatePagesDTO.builder()
                                .latest(limit(pages))
                                .build())
                        .build())
                .knowledge(knowledge)
                .build();
    }

    private CmsTemplateContextDTO.CmsTemplateKnowledgeDTO knowledge() {
        if (!capabilities.enabled("wiki")) {
            return CmsTemplateContextDTO.CmsTemplateKnowledgeDTO.builder()
                    .spaces(List.of()).pages(List.of()).latest(List.of()).build();
        }
        List<WikiSpace> publicSpaces = wikiSpaces.findAll().stream()
                .filter(WikiSpace::isPublicReadEnabled)
                .toList();
        List<CmsTemplateItemDTO> spaces = publicSpaces.stream().map(this::wikiSpace).toList();
        List<CmsTemplateItemDTO> pages = publicSpaces.stream()
                .flatMap(space -> publishedWikiPages(space).stream())
                .sorted(Comparator.comparing(CmsTemplateItemDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return CmsTemplateContextDTO.CmsTemplateKnowledgeDTO.builder()
                .spaces(spaces)
                .pages(pages)
                .latest(limit(pages))
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
                .updatedAt(format(page.getPublishedAt() == null ? page.getUpdateTime() : page.getPublishedAt()))
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

    private <T> List<T> limit(List<T> values) {
        return values.size() <= LATEST_LIMIT ? values : new ArrayList<>(values.subList(0, LATEST_LIMIT));
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
