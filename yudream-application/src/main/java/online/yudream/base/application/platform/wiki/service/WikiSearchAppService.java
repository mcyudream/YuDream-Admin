package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import online.yudream.base.domain.platform.wiki.service.WikiQueryExpansionGateway;
import online.yudream.base.domain.platform.wiki.service.WikiRerankGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;
import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多阶段检索：分词关键词 + 可选向量语义 + 可选图谱扩展 + 可选 rerank；
 * 支持“只读原文（source-grounded）”模式，仅从原始资料返回片段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiSearchAppService {
    private final CapabilityAppService capabilities;
    private final WikiSpaceRepo spaces;
    private final WikiNodeRepo nodes;
    private final WikiPageVersionRepo versions;
    private final WikiSourceRepo sources;
    private final WikiIndexGateway indexes;
    private final WikiQueryExpansionGateway expansions;
    private final WikiRerankGateway reranks;

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> search(String slug, String query, int topK, String prefix, boolean graph) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isExternalSearchEnabled()) {
            throw new BizException("该知识库未开放外部检索");
        }
        return searchPublic(space, query, topK, prefix, graph);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> search(String slug, String query, int topK, String prefix, boolean graph,
                                         boolean sourceGrounded) {
        return search(slug, query, topK, prefix, graph);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> searchForAdmin(String slug, String query, int topK, String prefix, boolean graph) {
        return search(slug, query, topK, prefix, graph, false, false);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> searchForAdmin(String slug, String query, int topK, String prefix, boolean graph,
                                                 boolean sourceGrounded) {
        return search(slug, query, topK, prefix, graph, sourceGrounded, false);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> searchForPublicSite(String slug, String query, int topK, String prefix, boolean graph) {
        return searchForPublicSite(slug, query, topK, prefix, graph, false);
    }

    /**
     * 公开站点检索：sourceGrounded 参数被忽略并强制为 false，避免匿名暴露原始资料全文；
     * 结果只包含已发布页面，并用发布版本正文重新生成片段，不信任管理端索引/草稿片段。
     */
    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> searchForPublicSite(String slug, String query, int topK, String prefix, boolean graph,
                                                      boolean sourceGrounded) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isPublicReadEnabled()) {
            throw new BizException("该知识库未开放公开阅读");
        }
        return searchPublic(space, query, topK, prefix, graph);
    }

    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> catalogForAdmin(String slug, int limit) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        List<WikiNode> all = nodes.findBySpaceId(space.getId());
        Map<Long, String> paths = slugPaths(all);
        return all.stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE)
                .sorted(Comparator.comparingInt(WikiNode::getSort)
                        .thenComparing(WikiNode::getTitle, Comparator.nullsLast(String::compareTo)))
                .limit(Math.max(1, limit))
                .map(node -> {
                    String path = paths.getOrDefault(node.getId(), node.getSlug());
                    return WikiSearchHitDTO.builder()
                            .score(0.01)
                            .nodeId(String.valueOf(node.getId()))
                            .kind("PAGE")
                            .title(node.getTitle())
                            .path(path)
                            .content(catalogExcerpt(node))
                            .sourceUrl(sourceUrl(space, path))
                            .build();
                })
                .toList();
    }

    /**
     * 聊天目录兜底只读取已发布版本。优先返回与问题相关的发布标题或摘要；
     * 没有相关标题/摘要时仅返回页面标题，不携带正文，避免随机内容和草稿泄漏。
     */
    @Transactional(readOnly = true)
    public List<WikiSearchHitDTO> catalogForChat(String slug, String query, int limit) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        return publishedPages(space).values().stream()
                .map(page -> chatCatalogHit(space, page, query))
                .sorted(Comparator.comparingDouble(WikiSearchHitDTO::getScore).reversed()
                        .thenComparing(WikiSearchHitDTO::getTitle, Comparator.nullsLast(String::compareTo)))
                .limit(Math.max(1, limit))
                .toList();
    }

    private WikiSearchHitDTO chatCatalogHit(WikiSpace space, PublishedPage page, String query) {
        WikiFrontmatter frontmatter = WikiFrontmatter.parse(page.version().getMarkdown());
        String title = publishedTitle(page.version(), frontmatter);
        List<String> queryTerms = tokenize(query);
        String phrase = normalizedPhrase(query);
        double titleScore = fieldScore(title, queryTerms, phrase);
        double summaryScore = fieldScore(frontmatter.summary(), queryTerms, phrase);
        boolean relevant = titleScore > 0 || summaryScore > 0;
        String content = summaryScore > 0
                ? truncate(frontmatter.summary(), 360)
                : titleScore > 0 ? truncate(frontmatter.bodyOnly(), 360) : "";
        return WikiSearchHitDTO.builder()
                .score(relevant ? 0.2 + Math.max(titleScore, summaryScore) : 0.01)
                .nodeId(String.valueOf(page.node().getId()))
                .kind("PAGE")
                .title(title)
                .path(page.path())
                .content(content)
                .sourceUrl(sourceUrl(space, page.path()))
                .build();
    }

    private String normalizedPhrase(String query) {
        return query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    }

    private String catalogExcerpt(WikiNode node) {
        if (node.getSummary() != null && !node.getSummary().isBlank()) {
            return truncate(node.getSummary(), 360);
        }
        return truncate(node.bodyMarkdown(), 360);
    }

    private List<WikiSearchHitDTO> search(String slug, String query, int topK, String prefix, boolean graph,
                                          boolean sourceGrounded, boolean requireExternal) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaces.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        if (requireExternal && !space.isExternalSearchEnabled()) {
            throw new BizException("该知识库未开放外部检索");
        }
        return searchAdmin(space, query, topK, prefix, graph, sourceGrounded);
    }

    private List<WikiSearchHitDTO> searchAdmin(WikiSpace space, String query, int topK, String prefix, boolean graph,
                                               boolean sourceGrounded) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (sourceGrounded) {
            return sourceGroundedSearch(space, query, topK);
        }
        List<WikiSearchHit> candidates = candidates(space, query, topK, prefix, graph);
        candidates = rerank(space, query, candidates);
        return candidates.stream().limit(Math.max(topK, 1))
                .map(hit -> pageHit(hit, space, adminMarkdown(hit))).toList();
    }

    private String adminMarkdown(WikiSearchHit hit) {
        return hit.nodeId() == null ? null
                : nodes.findById(hit.nodeId()).map(WikiNode::getMarkdownDraft).orElse(null);
    }

    private List<WikiSearchHitDTO> searchPublic(WikiSpace space, String query, int topK, String prefix, boolean graph) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Map<Long, PublishedPage> published = publishedPages(space);
        if (published.isEmpty()) {
            return List.of();
        }
        List<WikiSearchHit> candidates = publicCandidates(space, query, topK, prefix, graph, published);
        candidates = rerank(space, query, candidates);
        return candidates.stream().limit(Math.max(topK, 1))
                .map(hit -> pageHit(hit, space, publishedMarkdown(published.get(hit.nodeId())))).toList();
    }

    private String publishedMarkdown(PublishedPage page) {
        return page == null || page.version() == null ? null : page.version().getMarkdown();
    }

    /**
     * 公开候选完全基于发布版本构造，不触碰草稿关键词仓储，避免通过草稿关键词探知未发布内容。
     */
    private List<WikiSearchHit> publicCandidates(WikiSpace space, String query, int topK, String prefix, boolean graph,
                                                 Map<Long, PublishedPage> published) {
        List<WikiSearchHit> merged = new ArrayList<>();
        // 向量/图谱召回仍走索引，但命中必须先过滤到已发布节点并重新用发布版本物化。
        if (hasEmbedding(space)) {
            try {
                List<String> queries = new ArrayList<>(List.of(query));
                if (space.isQueryExpansionEnabled()) {
                    queries.addAll(expansions.expand(space.getGraphProviderCode(), space.getGraphModelCode(), query));
                }
                merged.addAll(queries.stream().flatMap(item -> indexes.search(space, item, Math.clamp(topK * 2, 4, 30), prefix,
                                graph && space.isGraphEnabled()).stream())
                        .filter(hit -> hit != null && hit.nodeId() != null && published.containsKey(hit.nodeId()))
                        .map(hit -> materializePublishedHit(hit, published.get(hit.nodeId()), query))
                        .toList());
            }
            catch (Exception exception) {
                log.warn("向量检索失败，已回退到关键词检索：{}", exception.getMessage());
            }
        }
        merged.addAll(publicKeywordHits(query, prefix, published, Math.clamp(topK * 2, 4, 30)));
        return dedup(merged);
    }

    /**
     * 召回并去重排序，返回候选（不限制最终条数，供后续 rerank/过滤）。
     */
    private List<WikiSearchHit> candidates(WikiSpace space, String query, int topK, String prefix, boolean graph) {
        List<WikiSearchHit> merged = new ArrayList<>();
        // 阶段 1：向量召回（可选，取决于空间是否配置了 embedding）；失败时回退到关键词，不影响整体检索
        if (hasEmbedding(space)) {
            try {
                List<String> queries = new ArrayList<>(List.of(query));
                if (space.isQueryExpansionEnabled()) {
                    queries.addAll(expansions.expand(space.getGraphProviderCode(), space.getGraphModelCode(), query));
                }
                merged.addAll(queries.stream().flatMap(item -> indexes.search(space, item, Math.clamp(topK * 2, 4, 30), prefix,
                                graph && space.isGraphEnabled()).stream()).toList());
            }
            catch (Exception exception) {
                log.warn("向量检索失败，已回退到关键词检索：{}", exception.getMessage());
            }
        }
        // 阶段 1.5：关键词召回（标题/正文/summary），并应用路径前缀
        merged.addAll(keywordHits(space, query, prefix, topK * 2));
        return dedup(merged);
    }

    private List<WikiSearchHit> dedup(List<WikiSearchHit> merged) {
        Map<String, WikiSearchHit> dedup = new LinkedHashMap<>();
        for (WikiSearchHit hit : merged) {
            String key = hit.nodeId() + ":" + hit.path();
            WikiSearchHit previous = dedup.get(key);
            if (previous == null || hit.score() > previous.score()) {
                dedup.put(key, hit);
            }
        }
        return dedup.values().stream()
                .sorted(Comparator.comparingDouble(WikiSearchHit::score).reversed()).toList();
    }

    private List<WikiSearchHit> rerank(WikiSpace space, String query, List<WikiSearchHit> candidates) {
        if (space.isRerankEnabled() && !candidates.isEmpty()) {
            try {
                return reranks.rerank(space.getEmbeddingProviderCode(), query, candidates);
            }
            catch (Exception exception) {
                log.warn("Rerank 失败，已使用原始排序：{}", exception.getMessage());
            }
        }
        return candidates;
    }

    private List<WikiSearchHitDTO> sourceGroundedSearch(WikiSpace space, String query, int topK) {
        return sources.searchByKeyword(space.getId(), query, Math.max(topK * 2, 1)).stream()
                .map(source -> WikiSearchHitDTO.builder()
                        .score(1.0)
                        .sourceId(String.valueOf(source.getId()))
                        .kind("SOURCE")
                        .title(source.getTitle())
                        .path(source.displayPath())
                        .content(excerptAroundQuery(source.getExtractedText(), query, 800))
                        .sourceUrl(source.getUrl())
                        .build())
                .limit(Math.max(topK, 1))
                .toList();
    }

    private List<WikiSearchHit> keywordHits(WikiSpace space, String query, String prefix, int limit) {
        return nodes.searchByKeyword(space.getId(), query, limit).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE)
                .filter(node -> matchesPrefix(node, prefix))
                .map(node -> new WikiSearchHit(0.85, node.getId(), node.getTitle(),
                        node.getAncestorPath() + node.getSlug(), excerptAroundQuery(node.bodyMarkdown(), query, 800)))
                .toList();
    }

    /**
     * 公开关键词召回：直接在已发布的版本 Markdown 上匹配 title/summary/body，不使用草稿关键词仓储。
     */
    private List<WikiSearchHit> publicKeywordHits(String query, String prefix, Map<Long, PublishedPage> published, int limit) {
        List<WikiSearchHit> hits = new ArrayList<>();
        for (PublishedPage page : published.values()) {
            if (!matchesPathPrefix(page.path(), prefix)) {
                continue;
            }
            WikiFrontmatter frontmatter = WikiFrontmatter.parse(page.version().getMarkdown());
            double score = keywordScore(query, frontmatter);
            if (score <= 0) {
                continue;
            }
            String title = publishedTitle(page.version(), frontmatter);
            hits.add(new WikiSearchHit(score, page.node().getId(), title, page.path(),
                    excerptAroundQuery(frontmatter.bodyOnly(), query, 800)));
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(WikiSearchHit::score).reversed())
                .limit(Math.max(limit, 1))
                .toList();
    }

    /**
     * 保守分词：按空白与标点拆分，保留中文整词/整短语；命中数越多分数越高，标题/摘要/正文依次降权。
     */
    private double keywordScore(String query, WikiFrontmatter frontmatter) {
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return 0.0;
        }
        String phrase = query.strip().toLowerCase(Locale.ROOT);
        double title = fieldScore(frontmatter.title(), terms, phrase);
        if (title > 0) {
            return 1.0 + title;
        }
        double summary = fieldScore(frontmatter.summary(), terms, phrase);
        if (summary > 0) {
            return 0.8 + summary;
        }
        double body = fieldScore(frontmatter.bodyOnly(), terms, phrase);
        if (body > 0) {
            return 0.7 + body;
        }
        return 0.0;
    }

    private double fieldScore(String value, List<String> terms, String phrase) {
        if (value == null) {
            return 0.0;
        }
        String text = value.toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                matched++;
            }
        }
        if (matched == 0) {
            return 0.0;
        }
        double ratio = (double) matched / terms.size();
        double phraseBonus = terms.size() > 1 && text.contains(phrase) ? 0.1 : 0.0;
        return ratio + phraseBonus;
    }

    private List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String token : query.strip().toLowerCase(Locale.ROOT).split("[\\p{P}\\s]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
        return terms;
    }

    private Map<Long, PublishedPage> publishedPages(WikiSpace space) {
        List<WikiNode> all = nodes.findBySpaceId(space.getId());
        Map<Long, String> paths = slugPaths(all);
        Map<Long, WikiNode> publishedNodes = all.stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE && node.getPublishedVersionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        WikiNode::getPublishedVersionId,
                        node -> node,
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (publishedNodes.isEmpty()) {
            return Map.of();
        }
        Map<Long, PublishedPage> result = new LinkedHashMap<>();
        for (WikiPageVersion version : versions.findByIds(publishedNodes.keySet())) {
            WikiNode node = publishedNodes.get(version.getId());
            if (node != null && node.getId().equals(version.getNodeId()) && node.getSpaceId().equals(version.getSpaceId())) {
                result.put(node.getId(), new PublishedPage(node, version, paths.getOrDefault(node.getId(), "")));
            }
        }
        return result;
    }

    /**
     * 依据 parentId 递归构造 slug 路径，与 {@link WikiAppService#tree} 的公开路由完全一致（无前导斜杠）。
     */
    private Map<Long, String> slugPaths(List<WikiNode> all) {
        Map<Long, WikiNode> byId = new LinkedHashMap<>();
        for (WikiNode node : all) {
            if (node != null && node.getId() != null) {
                byId.put(node.getId(), node);
            }
        }
        Map<Long, String> paths = new LinkedHashMap<>();
        Set<Long> visiting = new HashSet<>();
        for (WikiNode node : byId.values()) {
            slugPath(node, byId, paths, visiting);
        }
        return paths;
    }

    private String slugPath(WikiNode node, Map<Long, WikiNode> byId, Map<Long, String> paths, Set<Long> visiting) {
        String cached = paths.get(node.getId());
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(node.getId())) {
            return node.getSlug() == null ? "" : node.getSlug();
        }
        String slug = node.getSlug() == null ? "" : node.getSlug();
        String parentPath = "";
        if (node.getParentId() != null && byId.containsKey(node.getParentId())) {
            parentPath = slugPath(byId.get(node.getParentId()), byId, paths, visiting);
        }
        visiting.remove(node.getId());
        String path = parentPath.isBlank() ? slug : parentPath + "/" + slug;
        paths.put(node.getId(), path);
        return path;
    }

    private WikiSearchHit materializePublishedHit(WikiSearchHit hit, PublishedPage page, String query) {
        WikiFrontmatter frontmatter = WikiFrontmatter.parse(page.version().getMarkdown());
        String title = frontmatter.title().isBlank() ? page.node().getTitle() : frontmatter.title();
        return new WikiSearchHit(hit.score(), page.node().getId(), title,
                page.path(), excerptAroundQuery(frontmatter.bodyOnly(), query, 800));
    }

    private String publishedTitle(WikiPageVersion version, WikiFrontmatter frontmatter) {
        if (!frontmatter.title().isBlank()) {
            return frontmatter.title();
        }
        return version.getTitle() == null ? "" : version.getTitle();
    }

    private boolean matchesPathPrefix(String path, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return true;
        }
        String normalizedPrefix = normalizePath(prefix);
        String normalizedPath = normalizePath(path);
        return normalizedPath.equals(normalizedPrefix) || normalizedPath.startsWith(normalizedPrefix + "/");
    }

    private String normalizePath(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && !normalized.isEmpty()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean matchesPrefix(WikiNode node, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return true;
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String path = (node.getAncestorPath() == null ? "" : node.getAncestorPath()) + node.getSlug();
        return path.equals(normalized) || path.startsWith(normalized + "/");
    }

    private WikiSearchHitDTO pageHit(WikiSearchHit hit, WikiSpace space, String pageMarkdown) {
        return WikiSearchHitDTO.builder()
                .score(hit.score())
                .nodeId(String.valueOf(hit.nodeId()))
                .kind("PAGE")
                .title(hit.title())
                .path(hit.path())
                .content(hit.content())
                .sourceUrl(sourceUrl(space, hit.path()))
                .images(extractImages(pageMarkdown))
                .build();
    }

    /** 页面正文引用的站内图片：![alt](/api/files/{id}/content)，用于检索命中随带相关图片 */
    private static final Pattern PAGE_IMAGE = Pattern.compile(
            "!\\[([^\\]]*)]\\(\\s*(/api/files/\\d+/content)(\\s+(?:\"[^\"]*\"|'[^']*'))?\\s*\\)");
    private static final int MAX_HIT_IMAGES = 4;

    private List<WikiSearchHitDTO.Image> extractImages(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        Matcher matcher = PAGE_IMAGE.matcher(markdown);
        List<WikiSearchHitDTO.Image> images = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (matcher.find() && images.size() < MAX_HIT_IMAGES) {
            String url = matcher.group(2);
            if (seen.add(url)) {
                images.add(WikiSearchHitDTO.Image.builder().url(url).caption(matcher.group(1)).build());
            }
        }
        return images;
    }

    private boolean hasEmbedding(WikiSpace space) {
        return space.getEmbeddingProviderCode() != null && !space.getEmbeddingProviderCode().isBlank()
                && space.getEmbeddingModelCode() != null && !space.getEmbeddingModelCode().isBlank();
    }

    private String excerptAroundQuery(String value, String query, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.strip();
        if (text.length() <= limit) {
            return text;
        }
        int index = query == null ? -1 : text.toLowerCase().indexOf(query.strip().toLowerCase());
        if (index < 0) {
            return truncate(text, limit);
        }
        int start = Math.max(0, index - limit / 3);
        int end = Math.min(text.length(), start + limit);
        start = Math.max(0, end - limit);
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private static String sourceUrl(WikiSpace space, String path) {
        return "/wiki/" + space.getSlug() + "/" + (path == null ? "" : path.replaceFirst("^/+", ""));
    }

    private record PublishedPage(WikiNode node, WikiPageVersion version, String path) {
    }
}
