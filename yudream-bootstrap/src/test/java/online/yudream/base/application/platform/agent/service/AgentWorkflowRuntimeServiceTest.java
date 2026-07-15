package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.workflow.support.AgentKnowledgeOperations;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.common.exception.BizException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentWorkflowRuntimeServiceTest {

    @Test
    void shouldUseExplicitPermissionSnapshotWithoutReadingThreadContext() {
        ObjectProvider<AiGenerationGateway> gateways = mock(ObjectProvider.class);
        ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(gateways.getIfAvailable()).thenReturn(null);
        AiAgentTool protectedTool = new AiAgentTool() {
            @Override public AiAgentToolDescriptor descriptor() { return new AiAgentToolDescriptor("cms.patch", "Patch page", "", "cms:edit", "", "", "", Map.of()); }
            @Override public AiAgentToolResult execute(AiAgentToolCall call) { throw new AssertionError("Tool must not execute"); }
        };
        when(tools.stream()).thenReturn(Stream.of(protectedTool));
        AgentWorkflowRuntimeService service = new AgentWorkflowRuntimeService(
                new ObjectMapper(), mock(RuntimeExecutor.class), mock(AgentToolRepo.class), gateways, tools,
                mock(AgentKnowledgeOperations.class), mock(DocumentTextExtractor.class),
                permission -> { throw new AssertionError("Thread permission context must not be read"); },
                mock(online.yudream.base.application.platform.capability.service.CapabilityAppService.class)
        );
        AgentApplication application = AgentApplication.builder()
                .name("Async debug application").code("async-debug").toolCodes(List.of("cms.patch"))
                .workflowJson("""
                        {"nodes":[
                          {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                          {"id":"end","data":{"kind":"end","inputVariable":"query"}}
                        ],"edges":[{"source":"start","target":"end"}]}
                        """).build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("test");
        cmd.setPermissionCodes(List.of("cms:edit"));
        cmd.setPermissionContextExplicit(true);

        assertThat(service.execute(application, cmd, Map.of(), null, null, null).content()).isEqualTo("test");
    }

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
                mock(DocumentTextExtractor.class),
                permission -> true,
                mock(online.yudream.base.application.platform.capability.service.CapabilityAppService.class)
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
        assertThat(events.getFirst().message()).contains("输入：hello");
        assertThat(events.get(1).message()).contains("输出：hello");
    }

    @Test
    void shouldReturnEndNodeOutputInsteadOfLastExecutedSibling() {
        ObjectProvider<AiGenerationGateway> gateways = mock(ObjectProvider.class);
        ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(gateways.getIfAvailable()).thenReturn(null);
        when(tools.stream()).thenReturn(Stream.empty());
        AgentWorkflowRuntimeService service = new AgentWorkflowRuntimeService(
                new ObjectMapper(), mock(RuntimeExecutor.class), mock(AgentToolRepo.class), gateways, tools,
                mock(AgentKnowledgeOperations.class), mock(DocumentTextExtractor.class), permission -> true
                , mock(online.yudream.base.application.platform.capability.service.CapabilityAppService.class)
        );
        AgentApplication application = AgentApplication.builder()
                .name("分支应用").code("branch").toolCodes(List.of())
                .workflowJson("""
                        {"nodes":[
                          {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                          {"id":"end","data":{"kind":"end","inputVariable":"query"}},
                          {"id":"sibling","data":{"kind":"template","template":"later","outputVariable":"other"}}
                        ],"edges":[
                          {"source":"start","target":"end"},{"source":"start","target":"sibling"}
                        ]}
                        """)
                .build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("expected");

        assertThat(service.execute(application, cmd, Map.of(), null, null, null).content()).isEqualTo("expected");
    }

    @Test
    void shouldRejectSystemToolWhenCurrentRunnerLacksToolPermission() {
        ObjectProvider<AiGenerationGateway> gateways = mock(ObjectProvider.class);
        ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(gateways.getIfAvailable()).thenReturn(null);
        AiAgentTool protectedTool = new AiAgentTool() {
            @Override public AiAgentToolDescriptor descriptor() { return new AiAgentToolDescriptor("cms.patch", "修改页面", "", "cms:edit", "", "", "", Map.of()); }
            @Override public AiAgentToolResult execute(AiAgentToolCall call) { throw new AssertionError("不应执行"); }
        };
        when(tools.stream()).thenReturn(Stream.of(protectedTool));
        AgentWorkflowRuntimeService service = new AgentWorkflowRuntimeService(
                new ObjectMapper(), mock(RuntimeExecutor.class), mock(AgentToolRepo.class), gateways, tools,
                mock(AgentKnowledgeOperations.class), mock(DocumentTextExtractor.class), permission -> false
                , mock(online.yudream.base.application.platform.capability.service.CapabilityAppService.class)
        );
        AgentApplication application = AgentApplication.builder()
                .name("受限应用").code("protected").toolCodes(List.of("cms.patch"))
                .workflowJson("""
                        {"nodes":[
                          {"id":"start","data":{"kind":"start"}},
                          {"id":"end","data":{"kind":"end"}}
                        ],"edges":[{"source":"start","target":"end"}]}
                        """).build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("test");

        assertThatThrownBy(() -> service.execute(application, cmd, Map.of(), null, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权限调用工具");
    }
}
