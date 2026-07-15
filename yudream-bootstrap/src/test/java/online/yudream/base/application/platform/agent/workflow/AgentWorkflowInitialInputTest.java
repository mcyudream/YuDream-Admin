package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.handler.AgentEndNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentStartNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentWorkflowInitialInputTest {

    @Test
    void shouldExposeAttachmentsAndImageWithoutChangingUserInput() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(
                new AgentWorkflowGraphParser(objectMapper),
                List.of(new AgentStartNodeHandler(values), new AgentEndNodeHandler(values))
        );
        AgentWorkflowInitialInput input = new AgentWorkflowInitialInput(
                "question",
                Map.of("attachments", List.of(Map.of("name", "manual.pdf")), "imageDataUrl", "data:image/png;base64,x")
        );

        var result = executor.execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                  {"id":"end","data":{"kind":"end","inputVariable":"query"}}
                ],"edges":[{"source":"start","target":"end"}]}
                """, input);

        assertThat(result.context().nodeOutput("end")).isEqualTo("question");
        assertThat(result.context().variable("attachments")).isEqualTo(List.of(Map.of("name", "manual.pdf")));
        assertThat(result.context().variable("imageDataUrl")).isEqualTo("data:image/png;base64,x");
    }
}
