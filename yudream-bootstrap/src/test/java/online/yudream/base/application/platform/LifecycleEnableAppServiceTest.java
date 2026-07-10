package online.yudream.base.application.platform;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.dataviz.service.ChartAppService;
import online.yudream.base.application.platform.document.service.WordDocumentAppService;
import online.yudream.base.application.platform.graph.service.GraphAppService;
import online.yudream.base.application.platform.integration.service.IntegrationAppService;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.domain.platform.dataviz.repo.ChartDatasetGateway;
import online.yudream.base.domain.platform.dataviz.repo.ChartDefinitionRepo;
import online.yudream.base.domain.platform.dataviz.service.ChartDomainService;
import online.yudream.base.domain.platform.document.aggregate.WordTemplate;
import online.yudream.base.domain.platform.document.enumerate.TemplateStatus;
import online.yudream.base.domain.platform.document.repo.WordGenerationRecordRepo;
import online.yudream.base.domain.platform.document.repo.WordTemplateRepo;
import online.yudream.base.domain.platform.document.service.WordTemplateRenderer;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import online.yudream.base.domain.platform.graph.repo.GraphQueryLogRepo;
import online.yudream.base.domain.platform.graph.service.GraphDatabaseGateway;
import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.repo.HttpConnectorRepo;
import online.yudream.base.domain.platform.integration.repo.HttpInvocationLogRepo;
import online.yudream.base.domain.platform.integration.repo.RuntimeExecutionLogRepo;
import online.yudream.base.domain.platform.integration.repo.RuntimeScriptRepo;
import online.yudream.base.domain.platform.integration.service.HttpInvocationGateway;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LifecycleEnableAppServiceTest {

    @Test
    void enablesWordTemplate() {
        CapabilityModuleRepo capabilityRepo = enabledCapabilityRepo();
        WordTemplateRepo repo = mock(WordTemplateRepo.class);
        WordTemplate template = WordTemplate.builder().id(1L).templateFileId(11L)
                .status(TemplateStatus.DISABLED).build();
        when(repo.findById(1L)).thenReturn(Optional.of(template));
        WordDocumentAppService service = new WordDocumentAppService(
                capabilityRepo, repo, mock(WordGenerationRecordRepo.class),
                mock(WordTemplateRenderer.class), mock(FileAppService.class));

        service.enableTemplate(1L);

        assertThat(template.getStatus()).isEqualTo(TemplateStatus.ACTIVE);
        verify(repo).save(template);
    }

    @Test
    void enablesGraphConnectionWithoutOpeningExternalConnection() {
        CapabilityAppService capabilityService = mock(CapabilityAppService.class);
        GraphConnectionRepo repo = mock(GraphConnectionRepo.class);
        GraphDatabaseGateway gateway = mock(GraphDatabaseGateway.class);
        GraphConnection connection = GraphConnection.builder().id(2L)
                .status(GraphConnectionStatus.DISABLED).build();
        when(repo.findById(2L)).thenReturn(Optional.of(connection));
        GraphAppService service = new GraphAppService(
                capabilityService, repo, mock(GraphQueryLogRepo.class), gateway);

        service.enableConnection(2L);

        assertThat(connection.getStatus()).isEqualTo(GraphConnectionStatus.ACTIVE);
        verify(repo).save(connection);
        verifyNoInteractions(gateway);
    }

    @Test
    void enablesHttpConnector() {
        HttpConnectorRepo connectorRepo = mock(HttpConnectorRepo.class);
        HttpConnector connector = HttpConnector.builder().id(3L).status(ConnectorStatus.DISABLED).build();
        when(connectorRepo.findById(3L)).thenReturn(Optional.of(connector));
        IntegrationAppService service = integrationService(connectorRepo, mock(RuntimeScriptRepo.class));

        service.enableConnector(3L);

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.ACTIVE);
        verify(connectorRepo).save(connector);
    }

    @Test
    void enablesRuntimeScript() {
        RuntimeScriptRepo scriptRepo = mock(RuntimeScriptRepo.class);
        RuntimeScript script = RuntimeScript.builder().id(4L).status(ConnectorStatus.DISABLED).build();
        when(scriptRepo.findById(4L)).thenReturn(Optional.of(script));
        IntegrationAppService service = integrationService(mock(HttpConnectorRepo.class), scriptRepo);

        service.enableScript(4L);

        assertThat(script.getStatus()).isEqualTo(ConnectorStatus.ACTIVE);
        verify(scriptRepo).save(script);
    }

    @Test
    void enablesChartDefinition() {
        CapabilityAppService capabilityService = mock(CapabilityAppService.class);
        ChartDefinitionRepo repo = mock(ChartDefinitionRepo.class);
        ChartDefinition chart = ChartDefinition.builder().id(5L).enabled(false).build();
        when(repo.findById(5L)).thenReturn(Optional.of(chart));
        ChartAppService service = new ChartAppService(
                capabilityService, mock(ChartDomainService.class), repo, mock(ChartDatasetGateway.class));

        service.enableDefinition(5L);

        assertThat(chart.isEnabled()).isTrue();
        verify(repo).save(chart);
    }

    private CapabilityModuleRepo enabledCapabilityRepo() {
        CapabilityModuleRepo repo = mock(CapabilityModuleRepo.class);
        when(repo.findByCode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(CapabilityModule.builder().enabled(true).build()));
        return repo;
    }

    private IntegrationAppService integrationService(HttpConnectorRepo connectorRepo, RuntimeScriptRepo scriptRepo) {
        return new IntegrationAppService(
                enabledCapabilityRepo(),
                connectorRepo,
                mock(HttpInvocationLogRepo.class),
                scriptRepo,
                mock(RuntimeExecutionLogRepo.class),
                mock(HttpInvocationGateway.class),
                mock(RuntimeExecutor.class));
    }
}
