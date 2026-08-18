package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiGraphSnapshotDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识图谱：四信号关联度 + Louvain 社区检测 + 图谱洞察。
 */
@Service
@RequiredArgsConstructor
public class WikiGraphAnalysisAppService {

    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?]]");
    private static final double DIRECT_LINK_WEIGHT = 3.0;
    private static final double SOURCE_OVERLAP_WEIGHT = 4.0;
    private static final double ADAMIC_ADAR_WEIGHT = 1.5;
    private static final double TYPE_AFFINITY_WEIGHT = 1.0;
    private static final double LOW_COHESION_THRESHOLD = 0.15;
    private static final int MAX_NODES = 500;

    private final CapabilityAppService capabilities;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;

    @Transactional(readOnly = true)
    public WikiGraphSnapshotDTO snapshot(Long spaceId) {
        enabled();
        spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
        List<WikiNode> pages = nodeRepo.findBySpaceId(spaceId).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE && node.getPageType() != null)
                .limit(MAX_NODES)
                .toList();
        if (pages.isEmpty()) {
            return new WikiGraphSnapshotDTO(List.of(), List.of(), List.of(), List.of());
        }

        Map<String, WikiNode> byTitle = new HashMap<>();
        for (WikiNode page : pages) {
            byTitle.put(page.getTitle(), page);
        }
        Map<String, String> idByTitle = new HashMap<>();
        for (WikiNode page : pages) {
            idByTitle.put(page.getTitle(), String.valueOf(page.getId()));
        }

        Map<String, Map<String, Double>> weight = new HashMap<>();
        Map<String, Map<String, String>> signal = new HashMap<>();
        Map<String, Set<String>> neighbors = new HashMap<>();
        for (WikiNode page : pages) {
            String id = String.valueOf(page.getId());
            weight.put(id, new LinkedHashMap<>());
            signal.put(id, new LinkedHashMap<>());
            neighbors.put(id, new LinkedHashSet<>());
        }

        // 信号 1：直接 wikilink
        for (WikiNode page : pages) {
            String id = String.valueOf(page.getId());
            for (String title : linkedTitles(page.bodyMarkdown())) {
                String target = idByTitle.get(title);
                if (target != null && !target.equals(id)) {
                    add(weight, signal, neighbors, id, target, DIRECT_LINK_WEIGHT, "direct_link");
                }
            }
        }
        // 信号 2：来源重叠
        for (int i = 0; i < pages.size(); i++) {
            for (int j = i + 1; j < pages.size(); j++) {
                WikiNode left = pages.get(i);
                WikiNode right = pages.get(j);
                if (left.getSources() != null && right.getSources() != null
                        && shareAny(left.getSources(), right.getSources())) {
                    add(weight, signal, neighbors, String.valueOf(left.getId()), String.valueOf(right.getId()),
                            SOURCE_OVERLAP_WEIGHT, "source_overlap");
                }
            }
        }
        // 信号 3：Adamic-Adar（共享邻居）。先复制观测信号形成的邻居快照，
        // 整轮计算只从快照取值，避免本轮推断边污染后续节点对的共享邻居。
        Map<String, Set<String>> observedNeighbors = snapshotNeighbors(neighbors);
        List<Candidate> adamicAdarCandidates = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            for (int j = i + 1; j < pages.size(); j++) {
                String left = String.valueOf(pages.get(i).getId());
                String right = String.valueOf(pages.get(j).getId());
                double adamicAdar = adamicAdar(observedNeighbors, left, right);
                if (adamicAdar > 0) {
                    adamicAdarCandidates.add(new Candidate(left, right, ADAMIC_ADAR_WEIGHT * adamicAdar, "adamic_adar"));
                }
            }
        }
        for (Candidate candidate : adamicAdarCandidates) {
            add(weight, signal, neighbors, candidate.source(), candidate.target(),
                    candidate.weight(), candidate.signal());
        }
        // 信号 4：类型亲和
        for (int i = 0; i < pages.size(); i++) {
            for (int j = i + 1; j < pages.size(); j++) {
                WikiNode left = pages.get(i);
                WikiNode right = pages.get(j);
                if (left.getPageType() == right.getPageType()) {
                    add(weight, signal, neighbors, String.valueOf(left.getId()), String.valueOf(right.getId()),
                            TYPE_AFFINITY_WEIGHT, "type_affinity");
                }
            }
        }

        Map<String, String> communities = louvain(weight, pages);
        Map<String, List<String>> communityMembers = new HashMap<>();
        for (Map.Entry<String, String> entry : communities.entrySet()) {
            communityMembers.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }

        List<WikiGraphSnapshotDTO.Node> nodes = new ArrayList<>();
        for (WikiNode page : pages) {
            String id = String.valueOf(page.getId());
            nodes.add(new WikiGraphSnapshotDTO.Node(id, page.getTitle(), page.getPageType().name(),
                    neighbors.getOrDefault(id, Set.of()).size(), communities.getOrDefault(id, id)));
        }
        List<WikiGraphSnapshotDTO.Edge> edges = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> entry : weight.entrySet()) {
            String source = entry.getKey();
            for (Map.Entry<String, Double> target : entry.getValue().entrySet()) {
                if (source.compareTo(target.getKey()) < 0) {
                    edges.add(new WikiGraphSnapshotDTO.Edge(source, target.getKey(), target.getValue(),
                            signal.get(source).get(target.getKey())));
                }
            }
        }
        edges.sort(Comparator.comparingDouble(WikiGraphSnapshotDTO.Edge::weight).reversed());

        List<WikiGraphSnapshotDTO.Community> communitiesDto = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : communityMembers.entrySet()) {
            List<String> members = entry.getValue();
            double cohesion = cohesion(members, weight);
            communitiesDto.add(new WikiGraphSnapshotDTO.Community(entry.getKey(), label(members, byTitle),
                    members, members.size(), cohesion, cohesion < LOW_COHESION_THRESHOLD));
        }

        List<WikiGraphSnapshotDTO.Insight> insights = insights(pages, nodes, edges, communitiesDto, neighbors, byTitle);
        return new WikiGraphSnapshotDTO(nodes, edges, communitiesDto, insights);
    }

    private void add(Map<String, Map<String, Double>> weight, Map<String, Map<String, String>> signal,
                     Map<String, Set<String>> neighbors, String a, String b, double w, String s) {
        if (a.equals(b)) {
            return;
        }
        weight.get(a).merge(b, w, Double::sum);
        weight.get(b).merge(a, w, Double::sum);
        signal.get(a).put(b, s);
        signal.get(b).put(a, s);
        neighbors.get(a).add(b);
        neighbors.get(b).add(a);
    }

    /**
     * 复制不可变邻居快照，使 Adamic-Adar 只基于 direct_link/source_overlap 观测结果。
     */
    private Map<String, Set<String>> snapshotNeighbors(Map<String, Set<String>> neighbors) {
        Map<String, Set<String>> snapshot = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : neighbors.entrySet()) {
            snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return snapshot;
    }

    private double adamicAdar(Map<String, Set<String>> neighbors, String a, String b) {
        Set<String> left = neighbors.getOrDefault(a, Set.of());
        Set<String> right = neighbors.getOrDefault(b, Set.of());
        double score = 0;
        for (String common : left) {
            if (right.contains(common)) {
                int degree = neighbors.getOrDefault(common, Set.of()).size();
                if (degree > 1) {
                    score += 1.0 / Math.log(degree);
                }
            }
        }
        return score;
    }

    private Map<String, String> louvain(Map<String, Map<String, Double>> weight, List<WikiNode> pages) {
        Map<String, String> community = new HashMap<>();
        for (WikiNode page : pages) {
            community.put(String.valueOf(page.getId()), String.valueOf(page.getId()));
        }
        double m = 0;
        for (Map<String, Double> targets : weight.values()) {
            for (double w : targets.values()) {
                m += w;
            }
        }
        if (m == 0) {
            return community;
        }
        Map<String, Double> degree = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : weight.entrySet()) {
            degree.put(entry.getKey(), entry.getValue().values().stream().mapToDouble(Double::doubleValue).sum());
        }
        boolean changed = true;
        int passes = 0;
        while (changed && passes++ < 20) {
            changed = false;
            for (WikiNode page : pages) {
                String node = String.valueOf(page.getId());
                String currentCommunity = community.get(node);
                Map<String, Double> communityWeights = new HashMap<>();
                for (Map.Entry<String, Double> entry : weight.get(node).entrySet()) {
                    String targetCommunity = community.get(entry.getKey());
                    communityWeights.merge(targetCommunity, entry.getValue(), Double::sum);
                }
                double bestGain = 0;
                String bestCommunity = currentCommunity;
                double kI = degree.getOrDefault(node, 0.0);
                for (Map.Entry<String, Double> candidate : communityWeights.entrySet()) {
                    double kIIn = candidate.getValue();
                    double sumTot = 0;
                    for (String member : community.keySet()) {
                        if (community.get(member).equals(candidate.getKey())) {
                            sumTot += degree.getOrDefault(member, 0.0);
                        }
                    }
                    double gain = kIIn / m - (sumTot * kI) / (2 * m * m);
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestCommunity = candidate.getKey();
                    }
                }
                if (!bestCommunity.equals(currentCommunity)) {
                    community.put(node, bestCommunity);
                    changed = true;
                }
            }
        }
        return community;
    }

    private double cohesion(List<String> members, Map<String, Map<String, Double>> weight) {
        int size = members.size();
        if (size <= 1) {
            return 1.0;
        }
        Set<String> set = new HashSet<>(members);
        double internal = 0;
        for (String member : members) {
            for (Map.Entry<String, Double> entry : weight.getOrDefault(member, Map.of()).entrySet()) {
                if (set.contains(entry.getKey())) {
                    internal += entry.getValue();
                }
            }
        }
        internal /= 2;
        double possible = size * (size - 1) / 2.0;
        return possible == 0 ? 0 : internal / possible;
    }

    private String label(List<String> members, Map<String, WikiNode> byTitle) {
        return members.stream()
                .map(id -> {
                    for (Map.Entry<String, WikiNode> entry : byTitle.entrySet()) {
                        if (String.valueOf(entry.getValue().getId()).equals(id)) {
                            return entry.getKey();
                        }
                    }
                    return id;
                })
                .sorted(Comparator.comparingInt(String::length))
                .findFirst().orElse(members.get(0));
    }

    private List<WikiGraphSnapshotDTO.Insight> insights(List<WikiNode> pages, List<WikiGraphSnapshotDTO.Node> nodes,
                                                        List<WikiGraphSnapshotDTO.Edge> edges,
                                                        List<WikiGraphSnapshotDTO.Community> communities,
                                                        Map<String, Set<String>> neighbors, Map<String, WikiNode> byTitle) {
        List<WikiGraphSnapshotDTO.Insight> insights = new ArrayList<>();
        // 孤立页面
        for (WikiGraphSnapshotDTO.Node node : nodes) {
            if (node.degree() <= 1) {
                insights.add(new WikiGraphSnapshotDTO.Insight("ORPHAN",
                        "孤立页面", "页面「" + node.title() + "」缺少连接", List.of(node.id()), List.of(node.title())));
            }
        }
        // 稀疏社区
        for (WikiGraphSnapshotDTO.Community community : communities) {
            if (community.size() >= 3 && community.lowCohesion()) {
                insights.add(new WikiGraphSnapshotDTO.Insight("SPARSE_COMMUNITY", "稀疏社区",
                        "社区「" + community.label() + "」内聚度偏低（" + String.format("%.2f", community.cohesion()) + "）",
                        community.nodeIds(), List.of(community.label())));
            }
        }
        // 桥接节点（连接 3+ 社区）
        Map<String, Set<String>> nodeCommunities = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : neighbors.entrySet()) {
            Set<String> communitiesSeen = new HashSet<>();
            for (String neighbor : entry.getValue()) {
                for (WikiGraphSnapshotDTO.Node node : nodes) {
                    if (node.id().equals(neighbor)) {
                        communitiesSeen.add(node.community());
                        break;
                    }
                }
            }
            if (communitiesSeen.size() >= 3) {
                nodeCommunities.put(entry.getKey(), communitiesSeen);
            }
        }
        for (Map.Entry<String, Set<String>> entry : nodeCommunities.entrySet()) {
            String title = entry.getKey();
            for (WikiGraphSnapshotDTO.Node node : nodes) {
                if (node.id().equals(entry.getKey())) {
                    title = node.title();
                    break;
                }
            }
            insights.add(new WikiGraphSnapshotDTO.Insight("BRIDGE", "桥接节点",
                    "页面「" + title + "」连接了 " + entry.getValue().size() + " 个知识集群",
                    List.of(entry.getKey()), List.of(title)));
        }
        // 惊奇连接：跨社区边
        Map<String, String> communityById = new HashMap<>();
        for (WikiGraphSnapshotDTO.Node node : nodes) {
            communityById.put(node.id(), node.community());
        }
        edges.stream()
                .filter(edge -> !communityById.getOrDefault(edge.source(), "").equals(communityById.getOrDefault(edge.target(), "")))
                .sorted(Comparator.comparingDouble(WikiGraphSnapshotDTO.Edge::weight).reversed())
                .limit(5)
                .forEach(edge -> insights.add(new WikiGraphSnapshotDTO.Insight("SURPRISING_CONNECTION", "惊奇连接",
                        "跨社区关联，权重 " + String.format("%.1f", edge.weight()), List.of(edge.source(), edge.target()), List.of())));
        return insights;
    }

    private boolean shareAny(List<String> left, List<String> right) {
        Set<String> set = new HashSet<>(left);
        for (String item : right) {
            if (set.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private List<String> linkedTitles(String markdown) {
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

    private void enabled() {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
    }

    /**
     * Adamic-Adar 计算完成后暂存的推断边，待整轮计算结束再统一写入图结构。
     */
    private record Candidate(String source, String target, double weight, String signal) {
    }
}
