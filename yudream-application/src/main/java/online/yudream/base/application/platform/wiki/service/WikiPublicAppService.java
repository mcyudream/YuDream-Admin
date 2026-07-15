package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WikiPublicAppService {
    private final WikiSpaceRepo spaces;
    private final WikiPageVersionRepo versions;
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
        return published(wikiAppService.tree(space.getId()));
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> search(String spaceSlug, String query, int topK, String pathPrefix, boolean graphExpansion) {
        return searches.searchForPublicSite(spaceSlug, query, topK, pathPrefix, graphExpansion);
    }

    private WikiSpace publicSpace(String slug) {
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isPublicReadEnabled()) throw new BizException("该知识库未开放公开阅读");
        return space;
    }

    private List<WikiNodeDTO> published(List<WikiNodeDTO> nodes) {
        return nodes.stream().map(node -> {
            List<WikiNodeDTO> children = published(node.getChildren());
            if (node.getNodeType() == online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType.PAGE
                    && node.getPublishedVersionId() == null) return null;
            if (node.getNodeType() == online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType.DIRECTORY
                    && children.isEmpty()) return null;
            node.setChildren(children);
            if (node.getNodeType() == online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType.PAGE) {
                String markdown = versions.findById(Long.valueOf(node.getPublishedVersionId())).map(v -> v.getMarkdown()).orElse(null);
                if (markdown == null) return null;
                node.setMarkdown(markdown);
            }
            return node;
        }).filter(java.util.Objects::nonNull).toList();
    }
}
