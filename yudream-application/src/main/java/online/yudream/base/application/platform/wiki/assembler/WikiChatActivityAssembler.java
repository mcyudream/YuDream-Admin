package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 Wiki 预检索命中与页面聚合装配为 AG-UI 活动载荷。
 * <p>
 * 图谱只表达“本轮最多 5 条命中”的局部上下文：一个 query 合成节点、命中页面/来源节点、query 到命中的
 * retrieval_match 边，以及命中页面之间的 explicit_related/direct_link/source_overlap 真实关系边；
 * 最多展开一层显式 related/wikilink 邻居。节点数与边数受硬上限约束，避免把整库图谱快照透传给前端。
 */
public final class WikiChatActivityAssembler {

    private static final int MAX_HITS = 5;
    private static final int MAX_NODES = 24;
    private static final int MAX_EDGES = 48;
    private static final int MAX_EXCERPT = 320;
    private static final String QUERY_NODE_ID = "query";
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?]]");

    private WikiChatActivityAssembler() {
    }

    /**
     * 将检索命中转为活动展示片段。调用方传入的 pages 需已按场景过滤（公开场景只保留已发布页面），
     * 页面命中若不在 pages 中（例如公开场景下的未发布页）会被丢弃，从而避免泄露。
     */
    public static List<WikiChatActivityDTO.Hit> hits(List<WikiSearchHitDTO> hits, List<WikiNode> pages) {
        Map<String, WikiNode> byId = byId(pages);
        List<WikiChatActivityDTO.Hit> result = new ArrayList<>();
        for (WikiSearchHitDTO hit : safeList(hits)) {
            if (hit == null) {
                continue;
            }
            if (!"SOURCE".equals(hit.getKind()) && !byId.containsKey(hit.getNodeId())) {
                continue;
            }
            result.add(WikiChatActivityDTO.Hit.builder()
                    .score(hit.getScore())
                    .kind(nullToEmpty(hit.getKind()))
                    .nodeId(nullToEmpty(hit.getNodeId()))
                    .title(nullToEmpty(hit.getTitle()))
                    .path(nullToEmpty(hit.getPath()))
                    .excerpt(truncate(hit.getContent(), MAX_EXCERPT))
                    .build());
        }
        return result;
    }

    /**
     * 构造本轮局部图谱。调用方传入的 pages 需已按场景过滤，邻居解析只会在过滤后的页面集合内进行。
     */
    public static WikiChatActivityDTO.Graph graph(String question, List<WikiSearchHitDTO> hits, List<WikiNode> pages) {
        Map<String, WikiNode> byId = byId(pages);
        Map<String, WikiNode> byTitle = byTitle(pages);
        Map<String, WikiChatActivityDTO.Node> nodes = new LinkedHashMap<>();
        List<WikiChatActivityDTO.Edge> edges = new ArrayList<>();

        nodes.put(QUERY_NODE_ID, node(QUERY_NODE_ID, nullToEmpty(question), "query", "query", 0.0, ""));

        List<String> hitPageIds = new ArrayList<>();
        for (WikiSearchHitDTO hit : firstN(safeList(hits), MAX_HITS)) {
            if (hit == null) {
                continue;
            }
            String id;
            WikiChatActivityDTO.Node hitNode;
            if ("SOURCE".equals(hit.getKind())) {
                id = "source-" + nullToEmpty(hit.getSourceId());
                if ("source-".equals(id)) {
                    id = "source-" + nullToEmpty(hit.getTitle());
                }
                hitNode = node(id, nullToEmpty(hit.getTitle()), "source", "source", hit.getScore(), nullToEmpty(hit.getPath()));
            }
            else {
                WikiNode page = byId.get(hit.getNodeId());
                if (page == null) {
                    continue;
                }
                id = hit.getNodeId();
                hitNode = node(id, nullToEmpty(hit.getTitle()), typeOf(page), "page", hit.getScore(), nullToEmpty(hit.getPath()));
                hitPageIds.add(id);
            }
            if (nodes.containsKey(id)) {
                continue;
            }
            nodes.put(id, hitNode);
            addEdge(edges, QUERY_NODE_ID, id, retrievalWeight(hit.getScore()), "retrieval_match");
        }

        // 命中页面之间的真实关系边
        for (int i = 0; i < hitPageIds.size(); i++) {
            for (int j = i + 1; j < hitPageIds.size(); j++) {
                WikiNode left = byId.get(hitPageIds.get(i));
                WikiNode right = byId.get(hitPageIds.get(j));
                if (left == null || right == null) {
                    continue;
                }
                if (relatedTo(left, right)) {
                    addEdge(edges, hitPageIds.get(i), hitPageIds.get(j), 2.0, "explicit_related");
                }
                if (linksTo(left, right) || linksTo(right, left)) {
                    addEdge(edges, hitPageIds.get(i), hitPageIds.get(j), 3.0, "direct_link");
                }
                if (overlap(left.getSources(), right.getSources())) {
                    addEdge(edges, hitPageIds.get(i), hitPageIds.get(j), 4.0, "source_overlap");
                }
            }
        }

        // 最多展开一层显式 related/wikilink 邻居
        for (String hitPageId : hitPageIds) {
            if (nodes.size() >= MAX_NODES) {
                break;
            }
            WikiNode page = byId.get(hitPageId);
            if (page == null) {
                continue;
            }
            for (String title : safeList(page.getRelated())) {
                addNeighbor(nodes, edges, byTitle, hitPageId, title, 2.0, "explicit_related");
            }
            for (String title : linkedTitles(page.bodyMarkdown())) {
                addNeighbor(nodes, edges, byTitle, hitPageId, title, 3.0, "direct_link");
            }
        }

        return WikiChatActivityDTO.Graph.builder()
                .query(nullToEmpty(question))
                .nodes(new ArrayList<>(nodes.values()))
                .edges(edges)
                .build();
    }

    private static void addNeighbor(Map<String, WikiChatActivityDTO.Node> nodes,
                                    List<WikiChatActivityDTO.Edge> edges,
                                    Map<String, WikiNode> byTitle,
                                    String sourceId,
                                    String title,
                                    double weight,
                                    String signal) {
        if (nodes.size() >= MAX_NODES) {
            return;
        }
        WikiNode neighbor = byTitle.get(title);
        if (neighbor == null) {
            return;
        }
        String id = String.valueOf(neighbor.getId());
        if (nodes.containsKey(id)) {
            return;
        }
        nodes.put(id, node(id, nullToEmpty(neighbor.getTitle()), typeOf(neighbor), "neighbor", 0.0, pathOf(neighbor)));
        addEdge(edges, sourceId, id, weight, signal);
    }

    private static void addEdge(List<WikiChatActivityDTO.Edge> edges, String source, String target, double weight, String signal) {
        if (edges.size() >= MAX_EDGES || source == null || target == null || source.equals(target)) {
            return;
        }
        for (WikiChatActivityDTO.Edge edge : edges) {
            if (edge.source().equals(source) && edge.target().equals(target) && edge.signal().equals(signal)) {
                return;
            }
        }
        edges.add(WikiChatActivityDTO.Edge.builder()
                .source(source)
                .target(target)
                .weight(weight)
                .signal(signal)
                .build());
    }

    private static WikiChatActivityDTO.Node node(String id, String title, String type, String role, double score, String path) {
        return WikiChatActivityDTO.Node.builder()
                .id(id)
                .title(title)
                .type(type)
                .role(role)
                .score(score)
                .path(path)
                .build();
    }

    private static Map<String, WikiNode> byId(List<WikiNode> pages) {
        Map<String, WikiNode> map = new LinkedHashMap<>();
        for (WikiNode page : safeList(pages)) {
            if (page != null && page.getId() != null) {
                map.put(String.valueOf(page.getId()), page);
            }
        }
        return map;
    }

    private static Map<String, WikiNode> byTitle(List<WikiNode> pages) {
        Map<String, WikiNode> map = new LinkedHashMap<>();
        for (WikiNode page : safeList(pages)) {
            if (page != null && page.getTitle() != null && !page.getTitle().isBlank()) {
                map.putIfAbsent(page.getTitle(), page);
            }
        }
        return map;
    }

    private static boolean relatedTo(WikiNode left, WikiNode right) {
        return (left.getRelated() != null && left.getRelated().contains(right.getTitle()))
                || (right.getRelated() != null && right.getRelated().contains(left.getTitle()));
    }

    private static boolean linksTo(WikiNode source, WikiNode target) {
        return linkedTitles(source.bodyMarkdown()).contains(target.getTitle());
    }

    private static boolean overlap(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> linkedTitles(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<String> titles = new ArrayList<>();
        Matcher matcher = WIKILINK.matcher(markdown);
        while (matcher.find()) {
            titles.add(matcher.group(1).trim());
        }
        return titles;
    }

    private static double retrievalWeight(double score) {
        return score > 0 ? score : 1.0;
    }

    private static String typeOf(WikiNode node) {
        return node.getPageType() == null ? "page" : node.getPageType().name();
    }

    private static String pathOf(WikiNode node) {
        return nullToEmpty(node.getAncestorPath()) + nullToEmpty(node.getSlug());
    }

    private static List<WikiSearchHitDTO> firstN(List<WikiSearchHitDTO> hits, int limit) {
        if (hits.size() <= limit) {
            return hits;
        }
        return new ArrayList<>(hits.subList(0, limit));
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() > limit ? value.substring(0, limit) : value;
    }
}
