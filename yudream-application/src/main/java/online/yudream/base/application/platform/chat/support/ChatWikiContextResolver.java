package online.yudream.base.application.platform.chat.support;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.chat.dto.ChatContextRefDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.application.platform.wiki.service.WikiSearchAppService;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class ChatWikiContextResolver {
    private static final String WIKI_VIEW_PERMISSION = "platform:wiki:view";
    private static final int PER_SPACE_TOP_K = 3;
    private static final int PER_SPACE_CATALOG_LIMIT = 2;
    private static final int TOTAL_TOP_K = 8;

    private final WikiSearchAppService searchService;
    private final WikiSpaceRepo spaceRepo;

    public ChatWikiContextResolver(WikiSearchAppService searchService, WikiSpaceRepo spaceRepo) {
        this.searchService = searchService;
        this.spaceRepo = spaceRepo;
    }

    /**
     * 使用服务端捕获的权限快照解析聊天所选 Wiki，前端传入的空间标识不构成授权依据。
     */
    public ResolvedContext resolve(List<ChatContextRefDTO> contextRefs, String legacySpaceSlug, String question,
                                   List<String> permissionCodes) {
        ChatContextRefDTO wiki = selectedWiki(contextRefs, legacySpaceSlug);
        if (wiki == null) {
            return ResolvedContext.empty();
        }
        if (!hasWikiViewPermission(permissionCodes)) {
            return ResolvedContext.selectedWithoutContent(unauthorizedPrompt(wiki));
        }
        if (!StringUtils.hasText(question)) {
            return ResolvedContext.selectedWithoutContent(selectedWithoutContentPrompt(wiki, false));
        }
        boolean allSpaces = isAllSpaces(wiki.target());
        List<WikiSpace> spaces = allSpaces
                ? spaceRepo.findAll()
                : spaceRepo.findBySlug(wiki.target()).map(List::of).orElse(List.of());
        if (spaces.isEmpty()) {
            return ResolvedContext.selectedWithoutContent(selectedWithoutContentPrompt(wiki, false));
        }

        List<Hit> searchHits = new ArrayList<>();
        List<WikiSpace> spacesWithoutSearchHits = new ArrayList<>();
        for (WikiSpace space : spaces) {
            try {
                List<WikiSearchHitDTO> spaceHits = searchService.searchForAdmin(
                        space.getSlug(), question, PER_SPACE_TOP_K, null, true);
                if (spaceHits.isEmpty()) {
                    spacesWithoutSearchHits.add(space);
                }
                else {
                    spaceHits.forEach(hit -> searchHits.add(Hit.of(space, hit)));
                }
            }
            catch (Exception exception) {
                spacesWithoutSearchHits.add(space);
                log.warn("聊天 Wiki 检索失败，spaceSlug={}", space.getSlug(), exception);
            }
        }

        List<Hit> catalogHits = new ArrayList<>();
        int failedSpaces = 0;
        for (WikiSpace space : spacesWithoutSearchHits) {
            try {
                searchService.catalogForChat(space.getSlug(), question, PER_SPACE_CATALOG_LIMIT).stream()
                        .map(hit -> Hit.of(space, hit))
                        .forEach(catalogHits::add);
            }
            catch (Exception exception) {
                failedSpaces++;
                log.warn("聊天 Wiki 目录兜底失败，spaceSlug={}", space.getSlug(), exception);
            }
        }

        List<Hit> combinedHits = new ArrayList<>(searchHits);
        combinedHits.addAll(catalogHits);
        List<Hit> hits = limited(combinedHits);
        if (hits.isEmpty()) {
            return ResolvedContext.selectedWithoutContent(selectedWithoutContentPrompt(wiki, failedSpaces == spaces.size()));
        }
        boolean catalogOnly = searchHits.isEmpty();
        boolean searchAndCatalog = !searchHits.isEmpty() && !catalogHits.isEmpty();
        boolean catalogHasRelevantSummary = catalogHits.stream().anyMatch(hit -> StringUtils.hasText(hit.content()));
        StringBuilder prompt = new StringBuilder(catalogOnly
                ? catalogHasRelevantSummary
                        ? allSpaces
                                ? "\n\n以下是用户已选择的全部知识库目录摘要。请基于这些摘要回答，并说明目录摘要可能不包含问题的完整细节：\n"
                                : "\n\n以下是用户已选择的知识库目录摘要。检索未命中时请基于页面摘要回答，并说明摘要可能不包含问题的完整细节：\n"
                        : "\n\n知识库检索未命中，以下仅为已发布页面目录标题，不包含可用于回答的正文或摘要。请明确说明没有检索命中，不要根据标题臆测内容：\n"
                : searchAndCatalog
                        ? "\n\n以下是用户启用的知识库检索结果及未命中空间的目录摘要。仅在相关时引用，并在答案中标明来源页面：\n"
                        : "\n\n以下是用户启用的知识库检索结果。仅在相关时引用，并在答案中标明来源页面：\n");
        for (Hit hit : hits) {
            prompt.append("\n### [").append(hit.spaceName()).append("] ").append(hit.title()).append('\n');
            if (StringUtils.hasText(hit.content())) {
                prompt.append(hit.content()).append('\n');
            }
        }
        return new ResolvedContext(prompt.toString(), hits.stream().map(Hit::citation).toList());
    }

    private boolean hasWikiViewPermission(List<String> permissionCodes) {
        return permissionCodes != null
                && (permissionCodes.contains("*") || permissionCodes.contains(WIKI_VIEW_PERMISSION));
    }

    private ChatContextRefDTO selectedWiki(List<ChatContextRefDTO> contextRefs, String legacySpaceSlug) {
        List<ChatContextRefDTO> refs = contextRefs == null ? List.of() : contextRefs;
        return refs.stream()
                .filter(ref -> ref != null && "wiki".equalsIgnoreCase(ref.type()) && StringUtils.hasText(ref.target()))
                .findFirst()
                .orElseGet(() -> StringUtils.hasText(legacySpaceSlug)
                        ? new ChatContextRefDTO("wiki", legacySpaceSlug, legacySpaceSlug)
                        : null);
    }

    private boolean isAllSpaces(String target) {
        return "all".equalsIgnoreCase(target) || "__all__".equalsIgnoreCase(target);
    }

    private List<Hit> limited(List<Hit> hits) {
        return hits.stream()
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(TOTAL_TOP_K)
                .toList();
    }

    private String unauthorizedPrompt(ChatContextRefDTO wiki) {
        return isAllSpaces(wiki.target())
                ? "\n\n用户选择了全部知识库，但当前服务端权限快照无权读取 Wiki。请明确说明无权读取，不要臆测库内内容。"
                : "\n\n用户选择了知识库，但当前服务端权限快照无权读取 Wiki。请明确说明无权读取，不要臆测库内内容。";
    }

    private String selectedWithoutContentPrompt(ChatContextRefDTO wiki, boolean loadFailed) {
        if (loadFailed) {
            return isAllSpaces(wiki.target())
                    ? "\n\n用户已选择全部知识库，但本次知识库上下文加载失败。请明确说明暂时无法读取所选知识库，不要将其表述为用户未选择知识库，也不要臆测库内内容。"
                    : "\n\n用户已选择知识库，但本次知识库上下文加载失败。请明确说明暂时无法读取所选知识库，不要将其表述为用户未选择知识库，也不要臆测库内内容。";
        }
        return isAllSpaces(wiki.target())
                ? "\n\n用户已选择全部知识库，但未找到可用的目录或页面内容。请明确说明所选知识库当前没有可引用内容，不要将其表述为用户未选择知识库。"
                : "\n\n用户已选择知识库，但未找到可用的目录或页面内容。请明确说明所选知识库当前没有可引用内容，不要将其表述为用户未选择知识库。";
    }

    public record ResolvedContext(String prompt, List<ChatCitation> citations) {
        public static ResolvedContext empty() {
            return new ResolvedContext("", List.of());
        }

        public static ResolvedContext selectedWithoutContent(String prompt) {
            return new ResolvedContext(prompt, List.of());
        }
    }

    private record Hit(String spaceSlug, String spaceName, double score, String title, String content, ChatCitation citation) {
        private static Hit of(WikiSpace space, WikiSearchHitDTO hit) {
            String sourceUrl = hit.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                sourceUrl = "/wiki/" + space.getSlug() + "/" + (hit.getPath() == null ? "" : hit.getPath().replaceFirst("^/+", ""));
            }
            ChatCitation citation = new ChatCitation(hit.getTitle(), hit.getPath(), hit.getNodeId(), hit.getContent(),
                    space.getSlug(), space.getName(), sourceUrl, hitImages(hit));
            return new Hit(space.getSlug(), space.getName(), hit.getScore(), hit.getTitle(), hit.getContent(), citation);
        }

        private static List<ChatCitation.Image> hitImages(WikiSearchHitDTO hit) {
            if (hit.getImages() == null) {
                return List.of();
            }
            return hit.getImages().stream()
                    .map(image -> new ChatCitation.Image(image.getUrl(), image.getCaption()))
                    .toList();
        }
    }
}
