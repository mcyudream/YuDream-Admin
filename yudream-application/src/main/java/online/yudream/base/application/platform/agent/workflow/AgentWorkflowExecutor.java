package online.yudream.base.application.platform.agent.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentWorkflowExecutor {

    private final AgentWorkflowGraphParser parser;
    private final Map<String, AgentWorkflowNodeHandler> handlers;

    public AgentWorkflowExecutor(AgentWorkflowGraphParser parser, List<AgentWorkflowNodeHandler> handlers) {
        this.parser = parser;
        this.handlers = indexHandlers(handlers);
    }

    public AgentWorkflowExecution execute(String workflowJson, Object input) {
        return execute(workflowJson, input, AgentWorkflowEventListener.NOOP);
    }

    public AgentWorkflowExecution execute(
            String workflowJson,
            Object input,
            AgentWorkflowEventListener eventListener
    ) {
        AgentWorkflowGraph graph = parser.parse(workflowJson);
        AgentWorkflowContext context = new AgentWorkflowContext(input);
        AgentWorkflowEventListener listener = eventListener == null
                ? AgentWorkflowEventListener.NOOP
                : eventListener;
        Set<String> activatedNodeIds = new LinkedHashSet<>();
        List<String> executedNodeIds = new ArrayList<>();
        activatedNodeIds.add(graph.startNode().id());

        for (AgentWorkflowNode node : graph.topologicalOrder()) {
            if (!activatedNodeIds.contains(node.id())) {
                continue;
            }
            AgentWorkflowNodeHandler handler = handlers.get(node.kind());
            if (handler == null) {
                throw new AgentWorkflowDefinitionException("未注册节点处理器：" + node.kind());
            }
            listener.onNodeStarted(node, context);
            try {
                AgentWorkflowNodeResult result = handler.execute(node, context);
                if (result == null) {
                    throw new IllegalStateException("节点处理器不能返回 null：" + node.kind());
                }
                context.record(node.id(), result);
                executedNodeIds.add(node.id());
                listener.onNodeCompleted(node, result, context);
                activateTargets(graph, node, result.selectedSourceHandle(), activatedNodeIds);
            } catch (RuntimeException exception) {
                listener.onNodeFailed(node, exception, context);
                throw exception;
            }
        }
        Set<String> reachableNodeIds = structurallyReachableNodeIds(graph);
        graph.topologicalOrder().stream()
                .filter(node -> reachableNodeIds.contains(node.id()))
                .filter(node -> !executedNodeIds.contains(node.id()))
                .forEach(node -> listener.onNodeSkipped(node, context));
        return new AgentWorkflowExecution(context, executedNodeIds);
    }

    private Set<String> structurallyReachableNodeIds(AgentWorkflowGraph graph) {
        Set<String> reachable = new LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(graph.startNode().id());
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            if (!reachable.add(nodeId)) {
                continue;
            }
            graph.outgoingEdges(nodeId).forEach(edge -> queue.addLast(edge.target()));
        }
        return reachable;
    }

    private void activateTargets(
            AgentWorkflowGraph graph,
            AgentWorkflowNode node,
            String selectedSourceHandle,
            Set<String> activatedNodeIds
    ) {
        graph.outgoingEdges(node.id()).stream()
                .filter(edge -> selectedSourceHandle == null || selectedSourceHandle.equals(edge.sourceHandle()))
                .map(AgentWorkflowEdge::target)
                .forEach(activatedNodeIds::add);
    }

    private Map<String, AgentWorkflowNodeHandler> indexHandlers(List<AgentWorkflowNodeHandler> handlers) {
        Map<String, AgentWorkflowNodeHandler> indexed = new LinkedHashMap<>();
        if (handlers == null) {
            return Map.of();
        }
        for (AgentWorkflowNodeHandler handler : handlers) {
            if (handler == null || handler.kind() == null || handler.kind().isBlank()) {
                throw new IllegalArgumentException("节点处理器 kind 不能为空");
            }
            if (indexed.putIfAbsent(handler.kind(), handler) != null) {
                throw new IllegalArgumentException("节点处理器 kind 重复：" + handler.kind());
            }
        }
        return Map.copyOf(indexed);
    }
}
