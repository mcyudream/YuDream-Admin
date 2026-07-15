package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentWorkflowExecutorTest {

    private final AgentWorkflowGraphParser parser = new AgentWorkflowGraphParser(new ObjectMapper());

    @Test
    void parsesVueFlowNodesAndEdges() {
        AgentWorkflowGraph graph = parser.parse(workflow(
                """
                {"id":"start","data":{"kind":"start","title":"开始"}},
                {"id":"answer","data":{"kind":"llm","title":"回答"}}
                """,
                """
                {"id":"edge-1","source":"start","target":"answer","sourceHandle":"output"}
                """
        ));

        assertThat(graph.startNode().id()).isEqualTo("start");
        assertThat(graph.node("answer").kind()).isEqualTo("llm");
        assertThat(graph.outgoingEdges("start"))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.target()).isEqualTo("answer");
                    assertThat(edge.sourceHandle()).isEqualTo("output");
                });
    }

    @Test
    void rejectsWorkflowContainingCycle() {
        String json = workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"loop","data":{"kind":"code"}}
                """,
                """
                {"source":"start","target":"loop"},
                {"source":"loop","target":"start"}
                """
        );

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(AgentWorkflowDefinitionException.class)
                .hasMessageContaining("环");
    }

    @Test
    void executesOnlyNodesReachableFromStart() {
        AgentWorkflowExecutor executor = executor(
                handler("start", (node, context) -> AgentWorkflowNodeResult.output(context.input())),
                handler("template", (node, context) -> AgentWorkflowNodeResult.output("reachable")),
                handler("code", (node, context) -> AgentWorkflowNodeResult.output("orphan"))
        );
        String json = workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"reachable","data":{"kind":"template"}},
                {"id":"orphan","data":{"kind":"code"}}
                """,
                """
                {"source":"start","target":"reachable"}
                """
        );

        AgentWorkflowExecution execution = executor.execute(json, "hello");

        assertThat(execution.executedNodeIds()).containsExactly("start", "reachable");
        assertThat(execution.context().nodeOutput("orphan")).isNull();
    }

    @Test
    void propagatesNodeOutputsAndNamedVariables() {
        AgentWorkflowExecutor executor = executor(
                handler("start", (node, context) -> AgentWorkflowNodeResult
                        .output(context.input())
                        .withVariables(Map.of("query", context.input()))),
                handler("template", (node, context) -> AgentWorkflowNodeResult.output(
                        context.nodeOutput("start") + ":" + context.variable("query")
                ))
        );
        String json = workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"template","data":{"kind":"template"}}
                """,
                """
                {"source":"start","target":"template"}
                """
        );

        AgentWorkflowExecution execution = executor.execute(json, "hello");

        assertThat(execution.context().nodeOutput("template")).isEqualTo("hello:hello");
        assertThat(execution.context().variable("query")).isEqualTo("hello");
    }

    @Test
    void conditionSelectsOnlyMatchingSourceHandleAndEmitsEvents() {
        List<String> events = new ArrayList<>();
        AgentWorkflowEventListener listener = new AgentWorkflowEventListener() {
            @Override
            public void onNodeStarted(AgentWorkflowNode node, AgentWorkflowContext context) {
                events.add("start:" + node.id());
            }

            @Override
            public void onNodeCompleted(
                    AgentWorkflowNode node,
                    AgentWorkflowNodeResult result,
                    AgentWorkflowContext context
            ) {
                events.add("complete:" + node.id());
            }
        };
        AgentWorkflowExecutor executor = executor(
                handler("start", (node, context) -> AgentWorkflowNodeResult.output(context.input())),
                handler("condition", (node, context) -> AgentWorkflowNodeResult.branch(true, "true")),
                handler("template", (node, context) -> AgentWorkflowNodeResult.output(node.id()))
        );
        String json = workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"condition","data":{"kind":"condition"}},
                {"id":"yes","data":{"kind":"template"}},
                {"id":"no","data":{"kind":"template"}}
                """,
                """
                {"source":"start","target":"condition"},
                {"source":"condition","target":"yes","sourceHandle":"true"},
                {"source":"condition","target":"no","sourceHandle":"false"}
                """
        );

        AgentWorkflowExecution execution = executor.execute(json, "hello", listener);

        assertThat(execution.executedNodeIds()).containsExactly("start", "condition", "yes");
        assertThat(events).containsExactly(
                "start:start", "complete:start",
                "start:condition", "complete:condition",
                "start:yes", "complete:yes"
        );
    }

    @Test
    void conditionCanSelectFalseSourceHandle() {
        AgentWorkflowExecutor executor = executor(
                handler("start", (node, context) -> AgentWorkflowNodeResult.output(context.input())),
                handler("condition", (node, context) -> AgentWorkflowNodeResult.branch(false, "false")),
                handler("template", (node, context) -> AgentWorkflowNodeResult.output(node.id()))
        );
        String json = workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"condition","data":{"kind":"condition"}},
                {"id":"yes","data":{"kind":"template"}},
                {"id":"no","data":{"kind":"template"}}
                """,
                """
                {"source":"start","target":"condition"},
                {"source":"condition","target":"yes","sourceHandle":"true"},
                {"source":"condition","target":"no","sourceHandle":"false"}
                """
        );

        AgentWorkflowExecution execution = executor.execute(json, "hello");

        assertThat(execution.executedNodeIds()).containsExactly("start", "condition", "no");
    }

    @Test
    void rejectsReachableNodeWithoutRegisteredHandler() {
        AgentWorkflowExecutor executor = executor(
                handler("start", (node, context) -> AgentWorkflowNodeResult.output(context.input()))
        );
        String json = workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"llm","data":{"kind":"llm"}}
                """,
                """
                {"source":"start","target":"llm"}
                """
        );

        assertThatThrownBy(() -> executor.execute(json, "hello"))
                .isInstanceOf(AgentWorkflowDefinitionException.class)
                .hasMessageContaining("未注册节点处理器：llm");
    }

    private AgentWorkflowExecutor executor(AgentWorkflowNodeHandler... handlers) {
        return new AgentWorkflowExecutor(parser, List.of(handlers));
    }

    private AgentWorkflowNodeHandler handler(String kind, NodeExecution execution) {
        return new AgentWorkflowNodeHandler() {
            @Override
            public String kind() {
                return kind;
            }

            @Override
            public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
                return execution.execute(node, context);
            }
        };
    }

    private String workflow(String nodes, String edges) {
        return "{\"nodes\":[" + nodes + "],\"edges\":[" + edges + "]}";
    }

    @FunctionalInterface
    private interface NodeExecution {
        AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context);
    }
}
