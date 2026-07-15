package online.yudream.base.application.platform.agent.service;

import online.yudream.base.application.platform.agent.assembler.AgentModelCatalogParser;
import online.yudream.base.application.platform.agent.cmd.AgentApplicationSaveCmd;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowRuntimeResult;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowValidator;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentAppServiceLifecycleTest {

    @Test
    void newlyCreatedApplicationCannotBypassPublishEndpoint() {
        Fixture fixture = fixture();
        AgentApplicationSaveCmd command = saveCommand(AgentApplicationStatus.PUBLISHED);
        when(fixture.applicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = fixture.service.save(command);

        assertThat(result.getStatus()).isEqualTo(AgentApplicationStatus.DRAFT);
    }

    @Test
    void saveDerivesApplicationToolAuthorizationFromWorkflowInsteadOfClientInput() {
        Fixture fixture = fixture();
        AgentApplicationSaveCmd command = saveCommand(AgentApplicationStatus.DRAFT);
        command.setToolCodes(List.of("client.smuggled"));
        command.setWorkflowJson("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start"}},
                  {"id":"model","data":{"kind":"llm","toolCodes":["web.fetch","python.report","web.fetch"]}},
                  {"id":"legacy","data":{"kind":"tool","toolCode":"cms.patch"}},
                  {"id":"end","data":{"kind":"end"}}
                ],"edges":[
                  {"source":"start","target":"model"},
                  {"source":"model","target":"legacy"},
                  {"source":"legacy","target":"end"}
                ]}
                """);
        when(fixture.applicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.save(command);

        verify(fixture.applicationRepo).save(argThat(application -> application.getToolCodes()
                .equals(List.of("web.fetch", "python.report", "cms.patch"))));
    }

    @Test
    void saveClearsToolsForModelNodesExplicitlySwitchedToNone() {
        Fixture fixture = fixture();
        AgentApplicationSaveCmd command = saveCommand(AgentApplicationStatus.DRAFT);
        command.setToolCodes(List.of("client.smuggled"));
        command.setWorkflowJson("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start"}},
                  {"id":"model","data":{"kind":"llm","toolMode":"NONE","toolCodes":["stale.tool"]}},
                  {"id":"end","data":{"kind":"end"}}
                ],"edges":[
                  {"source":"start","target":"model"},
                  {"source":"model","target":"end"}
                ]}
                """);
        when(fixture.applicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.save(command);

        verify(fixture.applicationRepo).save(argThat(application -> application.getToolCodes().isEmpty()
                && application.getWorkflowJson().contains("\"toolCodes\":[]")));
    }

    @Test
    void saveClearsExistingAuthorizationWhenWorkflowHasNoToolDeclarations() {
        Fixture fixture = fixture();
        AgentApplication existing = application(AgentApplicationStatus.DRAFT);
        existing.setToolCodes(List.of("legacy.permitted"));
        when(fixture.applicationRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(fixture.applicationRepo.findByCode("test-agent")).thenReturn(Optional.of(existing));
        when(fixture.applicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentApplicationSaveCmd command = saveCommand(AgentApplicationStatus.DRAFT);
        command.setId(1L);
        command.setToolCodes(List.of("client.smuggled"));
        command.setWorkflowJson("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start"}},
                  {"id":"model","data":{"kind":"llm","providerCode":"openai","modelCode":"gpt-5","toolMode":"NONE","toolCodes":[" "]}},
                  {"id":"end","data":{"kind":"end"}}
                ],"edges":[
                  {"source":"start","target":"model"},
                  {"source":"model","target":"end"}
                ]}
                """);

        fixture.service.save(command);

        verify(fixture.applicationRepo).save(argThat(application -> application.getToolCodes().isEmpty()));
    }

    @Test
    void newLegacyWorkflowNeverAcceptsClientSuppliedApplicationTools() {
        Fixture fixture = fixture();
        AgentApplicationSaveCmd command = saveCommand(AgentApplicationStatus.DRAFT);
        command.setToolCodes(List.of("client.smuggled"));
        when(fixture.applicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.save(command);

        verify(fixture.applicationRepo).save(argThat(application -> application.getToolCodes().isEmpty()));
    }

    @Test
    void formalRunRequiresPublishedApplicationWhileDraftCanBeDebugged() {
        Fixture fixture = fixture();
        AgentApplication draft = application(AgentApplicationStatus.DRAFT);
        when(fixture.applicationRepo.findById(1L)).thenReturn(Optional.of(draft));
        AgentRunCmd command = new AgentRunCmd();
        command.setApplicationId(1L);
        command.setInput("hello");

        assertThatThrownBy(() -> fixture.service.run(command))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未发布");
        verify(fixture.workflowRuntime, never()).execute(any(), any(), any(), any(), any(), any());

        when(fixture.workflowRuntime.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AgentWorkflowRuntimeResult("ok", List.of()));
        AgentRunDTO result = fixture.service.debug(command, ignored -> {}, ignored -> {}, ignored -> {});
        assertThat(result.getContent()).isEqualTo("ok");
    }

    private Fixture fixture() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        CapabilityModuleRepo capabilityRepo = mock(CapabilityModuleRepo.class);
        AgentApplicationRepo applicationRepo = mock(AgentApplicationRepo.class);
        AgentToolRepo toolRepo = mock(AgentToolRepo.class);
        @SuppressWarnings("unchecked") ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(tools.stream()).thenReturn(Stream.empty());
        AgentWorkflowRuntimeService workflowRuntime = mock(AgentWorkflowRuntimeService.class);
        AgentAppService service = new AgentAppService(
                capabilities,
                capabilityRepo,
                applicationRepo,
                toolRepo,
                tools,
                mock(AgentModelCatalogParser.class),
                mock(WikiSpaceRepo.class),
                workflowRuntime,
                mock(AgentWorkflowValidator.class),
                permission -> true,
                mock(online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry.class)
        );
        return new Fixture(service, applicationRepo, workflowRuntime);
    }

    private AgentApplicationSaveCmd saveCommand(AgentApplicationStatus status) {
        AgentApplicationSaveCmd command = new AgentApplicationSaveCmd();
        command.setName("测试应用");
        command.setCode("test-agent");
        command.setStatus(status);
        command.setWorkflowJson("{\"nodes\":[{\"id\":\"start\",\"data\":{\"kind\":\"start\"}},{\"id\":\"end\",\"data\":{\"kind\":\"end\"}}],\"edges\":[{\"source\":\"start\",\"target\":\"end\"}]}");
        command.setToolCodes(List.of());
        return command;
    }

    private AgentApplication application(AgentApplicationStatus status) {
        return AgentApplication.builder()
                .id(1L)
                .name("测试应用")
                .code("test-agent")
                .status(status)
                .toolCodes(List.of())
                .workflowJson(saveCommand(status).getWorkflowJson())
                .build();
    }

    private record Fixture(
            AgentAppService service,
            AgentApplicationRepo applicationRepo,
            AgentWorkflowRuntimeService workflowRuntime
    ) {}
}
