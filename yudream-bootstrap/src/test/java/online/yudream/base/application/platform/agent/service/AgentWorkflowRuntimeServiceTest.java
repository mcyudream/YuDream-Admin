package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.workflow.support.AgentKnowledgeOperations;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentWorkflowRuntimeServiceTest {

    @Test
    void shouldRunWorkflowAndEmitRealNodeEventsWithoutSkippedFallback() {
        ObjectProvider<AiGenerationGateway> gateways = mock(ObjectProvider.class);
        ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(gateways.getIfAvailable()).thenReturn(null);
        when(tools.stream()).thenReturn(Stream.empty());
        AgentWorkflowRuntimeService service = new AgentWorkflowRuntimeService(
                new ObjectMapper(),
                mock(RuntimeExecutor.class),
                mock(AgentToolRepo.class),
                gateways,
                tools,
                mock(AgentKnowledgeOperations.class),
                mock(DocumentTextExtractor.class)
        );
        AgentApplication application = AgentApplication.builder()
                .name("模板应用")
                .code("template")
                .toolCodes(List.of())
                .workflowJson("""
                        {"nodes":[
                          {"id":"start","data":{"kind":"start","title":"开始","outputVariable":"query"}},
                          {"id":"template","data":{"kind":"template","title":"模板","template":"收到：{{query}}","outputVariable":"answer"}},
                          {"id":"end","data":{"kind":"end","title":"结束","inputVariable":"answer"}}
                        ],"edges":[
                          {"source":"start","target":"template"},{"source":"template","target":"end"}
                        ]}
                        """)
                .build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("hello");
        List<AgentDebugEventDTO> events = new ArrayList<>();

        var result = service.execute(application, cmd, Map.of(), events::add, null, null);

        assertThat(result.content()).isEqualTo("收到：hello");
        assertThat(result.toolResults()).isEmpty();
        assertThat(events).extracting(AgentDebugEventDTO::status)
                .containsExactly("RUNNING", "COMPLETED", "RUNNING", "COMPLETED", "RUNNING", "COMPLETED");
        assertThat(events).noneMatch(event -> "SKIPPED".equals(event.status()));
    }
}
