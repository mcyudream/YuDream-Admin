package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves both persisted ancestor IDs and public slug paths from parent links, never from cached paths. */
public final class WikiNodePaths {
    public static final int MAX_PARENT_CHAIN_DEPTH = 200;

    private final Long spaceId;
    private final Map<Long, WikiNode> byId = new HashMap<>();

    public WikiNodePaths(Long spaceId, List<WikiNode> nodes) {
        this.spaceId = spaceId;
        for (WikiNode node : nodes) {
            if (node.getId() != null && byId.putIfAbsent(node.getId(), node) != null) {
                throw new BizException("知识库存在重复节点 ID");
            }
        }
    }

    public Paths resolve(WikiNode node) {
        return resolve(node, null, null);
    }

    /** Resolves a proposed move without mutating any node or writing to storage. */
    public Paths resolve(WikiNode node, Long movedId, Long targetParentId) {
        if (!spaceId.equals(node.getSpaceId())) {
            throw new BizException("节点不属于当前知识库");
        }
        List<String> ids = new ArrayList<>();
        List<String> slugs = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        if (node.getId() != null) {
            visited.add(node.getId());
        }
        slugs.add(node.getSlug() == null ? "" : node.getSlug());
        WikiNode current = node;
        int depth = 0;
        while (true) {
            Long parentId = movedId != null && movedId.equals(current.getId())
                    ? targetParentId : current.getParentId();
            if (parentId == null) {
                break;
            }
            if (depth++ >= MAX_PARENT_CHAIN_DEPTH) {
                throw new BizException("Wiki 节点层级过深（>" + MAX_PARENT_CHAIN_DEPTH + "）");
            }
            if (!visited.add(parentId)) {
                throw new BizException("Wiki 节点父链存在循环");
            }
            WikiNode parent = byId.get(parentId);
            if (parent == null || !spaceId.equals(parent.getSpaceId())) {
                throw new BizException("父节点不存在或不属于当前知识库");
            }
            if (parent.getNodeType() != WikiNodeType.DIRECTORY) {
                throw new BizException("页面节点不能包含子节点");
            }
            ids.add(parentId.toString());
            slugs.add(parent.getSlug() == null ? "" : parent.getSlug());
            current = parent;
        }
        java.util.Collections.reverse(ids);
        java.util.Collections.reverse(slugs);
        return new Paths(ids.isEmpty() ? "/" : "/" + String.join("/", ids) + "/", String.join("/", slugs));
    }

    public record Paths(String ancestorIds, String slugPath) {
    }
}
