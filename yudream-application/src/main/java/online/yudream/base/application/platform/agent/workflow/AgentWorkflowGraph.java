package online.yudream.base.application.platform.agent.workflow;

import java.util.List;
import java.util.Map;

public final class AgentWorkflowGraph {

    private final Map<String, AgentWorkflowNode> nodes;
    private final Map<String, List<AgentWorkflowEdge>> outgoingEdges;
    private final List<AgentWorkflowNode> topologicalOrder;
    private final AgentWorkflowNode startNode;

    AgentWorkflowGraph(
            Map<String, AgentWorkflowNode> nodes,
            Map<String, List<AgentWorkflowEdge>> outgoingEdges,
            List<AgentWorkflowNode> topologicalOrder,
            AgentWorkflowNode startNode
    ) {
        this.nodes = Map.copyOf(nodes);
        this.outgoingEdges = outgoingEdges.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))
        );
        this.topologicalOrder = List.copyOf(topologicalOrder);
        this.startNode = startNode;
    }

    public AgentWorkflowNode startNode() {
        return startNode;
    }

    public AgentWorkflowNode node(String id) {
        AgentWorkflowNode node = nodes.get(id);
        if (node == null) {
            throw new AgentWorkflowDefinitionException("工作流节点不存在：" + id);
        }
        return node;
    }

    public List<AgentWorkflowEdge> outgoingEdges(String nodeId) {
        return outgoingEdges.getOrDefault(nodeId, List.of());
    }

    public List<AgentWorkflowNode> topologicalOrder() {
        return topologicalOrder;
    }
}
