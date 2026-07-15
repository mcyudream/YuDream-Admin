package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.handler.AgentConditionNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentEndNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentStartNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentTemplateNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDeterministicWorkflowTest {

    @Test
    void shouldRenderTemplateAndExecuteOnlySelectedConditionBranch() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(
                new AgentWorkflowGraphParser(objectMapper),
                List.of(
                        new AgentStartNodeHandler(values),
                        new AgentTemplateNodeHandler(values),
                        new AgentConditionNodeHandler(values),
                        new AgentEndNodeHandler(values)
                )
        );

        AgentWorkflowExecution execution = executor.execute("""
                {
                  "nodes": [
                    {"id":"start","data":{"kind":"start","title":"开始","outputVariable":"query"}},
                    {"id":"prepare","data":{"kind":"template","title":"模板","template":"订单：{{query}}","outputVariable":"message"}},
                    {"id":"judge","data":{"kind":"condition","title":"判断","condition":"message == '订单：urgent'"}},
                    {"id":"yes","data":{"kind":"template","title":"加急","template":"已加急","outputVariable":"result"}},
                    {"id":"no","data":{"kind":"template","title":"普通","template":"普通订单","outputVariable":"result"}},
                    {"id":"end","data":{"kind":"end","title":"结束","inputVariable":"result"}}
                  ],
                  "edges": [
                    {"source":"start","target":"prepare"},
                    {"source":"prepare","target":"judge"},
                    {"source":"judge","sourceHandle":"true","target":"yes"},
                    {"source":"judge","sourceHandle":"false","target":"no"},
                    {"source":"yes","target":"end"},
                    {"source":"no","target":"end"}
                  ]
                }
                """, "urgent");

        assertThat(execution.executedNodeIds()).containsExactly("start", "prepare", "judge", "yes", "end");
        assertThat(execution.context().nodeOutput("end")).isEqualTo("已加急");
        assertThat(execution.context().nodeOutput("no")).isNull();
    }
}
