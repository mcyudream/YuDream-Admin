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
        return executeRemaining(graph, context, listener, activatedNodeIds, executedNodeIds, 0);
    }

    public AgentWorkflowExecution resume(String workflowJson,
            online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame frame,
            AgentWorkflowNodeResult nativeNodeResult, AgentWorkflowEventListener eventListener) {
        AgentWorkflowGraph graph = parser.parse(workflowJson);
        AgentWorkflowEventListener listener = eventListener == null ? AgentWorkflowEventListener.NOOP : eventListener;
        if (frame.cursor() < 0 || frame.cursor() >= graph.topologicalOrder().size()
                || !graph.topologicalOrder().get(frame.cursor()).id().equals(frame.pausedNodeId())) {
            throw new AgentWorkflowDefinitionException("工作流检查点与原始图不匹配");
        }
        AgentWorkflowContext context = AgentWorkflowContext.restore(frame);
        Set<String> activated = new LinkedHashSet<>(frame.activatedNodeIds());
        List<String> executed = new ArrayList<>(frame.executedNodeIds());
        AgentWorkflowNode node = graph.topologicalOrder().get(frame.cursor());
        context.record(node.id(), nativeNodeResult);
        executed.add(node.id());
        listener.onNodeCompleted(node, nativeNodeResult, context);
        activateTargets(graph, node, nativeNodeResult.selectedSourceHandle(), activated);
        listener.onCheckpoint(frame(frame.cursor() + 1, null, context, activated, executed, null));
        return executeRemaining(graph, context, listener, activated, executed, frame.cursor() + 1);
    }

    private AgentWorkflowExecution executeRemaining(AgentWorkflowGraph graph, AgentWorkflowContext context,
            AgentWorkflowEventListener listener, Set<String> activatedNodeIds, List<String> executedNodeIds, int cursor) {
        for (int index = cursor; index < graph.topologicalOrder().size(); index++) {
            AgentWorkflowNode node = graph.topologicalOrder().get(index);
            if (!activatedNodeIds.contains(node.id())) {
                continue;
            }
            AgentWorkflowNodeHandler handler = handlers.get(node.kind());
            if (handler == null) {
                throw new AgentWorkflowDefinitionException("未注册节点处理器：" + node.kind());
            }
            // 在真实业务节点开始前记账；重启后尚未核实的副作用不会被自动重跑。
            listener.onCheckpoint(frame(index, node.id(), context, activatedNodeIds, executedNodeIds, null));
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
                listener.onCheckpoint(frame(index + 1, null, context, activatedNodeIds, executedNodeIds, null));
            } catch (AgentWorkflowSuspendedException suspended) {
                var checkpoint = frame(index, node.id(), context, activatedNodeIds, executedNodeIds, suspended.nativeResult());
                listener.onCheckpoint(checkpoint);
                listener.onNodeSuspended(node, context);
                return new AgentWorkflowExecution(context, executedNodeIds, checkpoint);
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

    private online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame frame(int cursor, String pausedNode,
            AgentWorkflowContext context, Set<String> active, List<String> executed,
            online.yudream.base.domain.platform.ai.valobj.HarnessRunResult suspended) {
        return new online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame(cursor, pausedNode,
                context.input(), List.copyOf(active), executed, context.nodeOutputs(), context.variables(), suspended);
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
