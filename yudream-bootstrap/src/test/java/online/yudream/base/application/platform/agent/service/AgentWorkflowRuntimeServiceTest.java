package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.workflow.support.AgentKnowledgeOperations;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentWorkflowRuntimeServiceTest {

    @Test
    void shouldRunExtractNodeWithoutEnablingIntegrationForSystemModelTool() {
        ObjectProvider<AiGenerationGateway> gateways = mock(ObjectProvider.class);
        ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        AiGenerationGateway gateway = new AiGenerationGateway() {
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                return new AiGenerationResult(null, "{\"intent\":\"refund\"}", null, null, null, null, null, List.of(), List.of());
            }
            @Override public AiGenerationResult generateStream(AiGenerationRequest request,
                    java.util.function.Consumer<String> onDelta,
                    java.util.function.Consumer<AiAgentToolResult> onTool,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> onProgress) {
                return generate(request);
            }
        };
        AiAgentTool systemTool = new AiAgentTool() {
            @Override public AiAgentToolDescriptor descriptor() {
                return new AiAgentToolDescriptor("web.fetch", "Fetch", "", "", "", "", "", Map.of());
            }
            @Override public AiAgentToolResult execute(AiAgentToolCall call) { return null; }
        };
        when(gateways.getIfAvailable()).thenReturn(gateway);
        when(tools.stream()).thenReturn(Stream.of(systemTool));
        var capability = mock(online.yudream.base.application.platform.capability.service.CapabilityAppService.class);
        AgentWorkflowRuntimeService service = new AgentWorkflowRuntimeService(
                new ObjectMapper(), mock(RuntimeExecutor.class), mock(AgentToolRepo.class), gateways, tools,
                mock(AgentKnowledgeOperations.class), mock(DocumentTextExtractor.class), permission -> true, capability
        );
        AgentApplication application = AgentApplication.builder().name("Extract").code("extract")
                .toolCodes(List.of("web.fetch"))
                .workflowJson("""
                        {"nodes":[
                          {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                          {"id":"extract","data":{"kind":"extract","providerCode":"p","modelCode":"m",
                            "inputVariable":"query","outputVariable":"result","toolCodes":["web.fetch"],"toolMode":"AUTO"}},
                          {"id":"end","data":{"kind":"end","inputVariable":"result"}}
                        ],"edges":[{"source":"start","target":"extract"},{"source":"extract","target":"end"}]}
                        """).build();
        AgentRunCmd command = new AgentRunCmd();
        command.setInput("refund request");

        assertThat(service.execute(application, command, Map.of(), null, null, null).content())
                .isEqualTo("{\"intent\":\"refund\"}");
        verifyNoInteractions(capability);
    }

    @Test
    void shouldEnableIntegrationWhenModelNodeSelectsPythonTool() {
        ObjectProvider<AiGenerationGateway> gateways = mock(ObjectProvider.class);
        ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        AgentToolRepo toolRepo = mock(AgentToolRepo.class);
        AgentTool pythonTool = AgentTool.python("Risk score", "risk_score", "def run(params: dict) -> dict:\n    return {'score': 1}");
        when(toolRepo.findByCode("risk_score")).thenReturn(Optional.of(pythonTool));
        when(tools.stream()).thenReturn(Stream.empty());
        when(gateways.getIfAvailable()).thenReturn(new AiGenerationGateway() {
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                return new AiGenerationResult(null, "done", null, null, null, null, null, List.of(), List.of());
            }
        });
        var capability = mock(online.yudream.base.application.platform.capability.service.CapabilityAppService.class);
        AgentWorkflowRuntimeService service = new AgentWorkflowRuntimeService(
                new ObjectMapper(), mock(RuntimeExecutor.class), toolRepo, gateways, tools,
                mock(AgentKnowledgeOperations.class), mock(DocumentTextExtractor.class), permission -> true, capability
        );
        AgentApplication application = AgentApplication.builder().name("Python model").code("python-model")
                .toolCodes(List.of("risk_score"))
                .workflowJson("""
                        {"nodes":[
                          {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                          {"id":"model","data":{"kind":"llm","providerCode":"p","modelCode":"m",
                            "inputVariable":"query","outputVariable":"answer","toolCodes":["risk_score"]}},
                          {"id":"end","data":{"kind":"end","inputVariable":"answer"}}
                        ],"edges":[{"source":"start","target":"model"},{"source":"model","target":"end"}]}
                        """).build();
        AgentRunCmd command = new AgentRunCmd();
        command.setInput("score it");

        service.execute(application, command, Map.of(), null, null, null);

        verify(capability).ensureEnabled("integration", "集成与 Python 运行时");
    }

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
        when(gateways.getIfAvailable()).thenReturn(new AiGenerationGateway() {
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                return new AiGenerationResult(null, "unused", null, null, null, null, null, List.of(), List.of());
            }
        });
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
                          {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                          {"id":"model","data":{"kind":"llm","providerCode":"p","modelCode":"m",
                            "inputVariable":"query","outputVariable":"answer","toolCodes":["cms.patch"]}},
                          {"id":"end","data":{"kind":"end","inputVariable":"answer"}}
                        ],"edges":[{"source":"start","target":"model"},{"source":"model","target":"end"}]}
                        """).build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("test");

        assertThatThrownBy(() -> service.execute(application, cmd, Map.of(), null, null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("No permission to invoke tool");
    }
}
