package online.yudream.base.application.platform.chat.support;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.application.platform.wiki.service.WikiChatAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WikiChatDispatcher implements ChatDispatcher {

    private final WikiChatAppService wikiChatAppService;

    @Override
    public ChatScopeType scopeType() {
        return ChatScopeType.WIKI;
    }

    @Override
    public ChatDispatchResult dispatch(ChatDispatchContext context) {
        if (!StringUtils.hasText(context.spaceSlug())) {
            throw new BizException("请选择要问答的知识库");
        }
        WikiChatResultDTO result = wikiChatAppService.chatStreamBySlug(
                context.spaceSlug(),
                context.question(),
                context.history(),
                context.onDelta(),
                context.onReasoningDelta(),
                context.onTool(),
                activity -> context.onActivity().accept(toActivity(activity)));
        List<ChatCitation> citations = result == null || result.citations() == null
                ? List.of()
                : result.citations().stream()
                        .map(citation -> new ChatCitation(citation.title(), citation.path(), citation.nodeId(), citation.excerpt(),
                                context.spaceSlug(), null, null, citationImages(citation)))
                        .toList();
        return ChatDispatchResult.of(result == null ? "" : result.answer(), result == null ? AiUsage.empty() : result.usage(), citations);
    }

    private List<ChatCitation.Image> citationImages(WikiChatResultDTO.Citation citation) {
        if (citation.images() == null) {
            return List.of();
        }
        return citation.images().stream()
                .map(image -> new ChatCitation.Image(image.url(), image.caption()))
                .toList();
    }

    private ChatActivity toActivity(WikiChatActivityDTO activity) {
        if (activity == null) {
            return null;
        }
        return new ChatActivity(
                activity.activityType(),
                activity.phase(),
                activity.status(),
                activity.title(),
                activity.content(),
                activity.query(),
                hits(activity.hits()),
                graph(activity.graph()));
    }

    private List<Map<String, Object>> hits(List<WikiChatActivityDTO.Hit> hits) {
        if (hits == null) {
            return null;
        }
        return hits.stream().map(hit -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("score", hit.score());
            item.put("kind", hit.kind());
            item.put("nodeId", hit.nodeId());
            item.put("title", hit.title());
            item.put("path", hit.path());
            item.put("excerpt", hit.excerpt());
            return item;
        }).toList();
    }

    private Map<String, Object> graph(WikiChatActivityDTO.Graph graph) {
        if (graph == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", graph.query());
        payload.put("nodes", graph.nodes() == null ? List.of() : graph.nodes().stream().map(node -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", node.id());
            item.put("title", node.title());
            item.put("type", node.type());
            item.put("role", node.role());
            item.put("score", node.score());
            item.put("path", node.path());
            return item;
        }).toList());
        payload.put("edges", graph.edges() == null ? List.of() : graph.edges().stream().map(edge -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", edge.source());
            item.put("target", edge.target());
            item.put("weight", edge.weight());
            item.put("signal", edge.signal());
            return item;
        }).toList());
        return payload;
    }
}
