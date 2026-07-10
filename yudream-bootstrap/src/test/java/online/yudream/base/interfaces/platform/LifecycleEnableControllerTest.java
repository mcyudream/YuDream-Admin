package online.yudream.base.interfaces.platform;

import online.yudream.base.application.platform.dataviz.service.ChartAppService;
import online.yudream.base.application.platform.document.service.WordDocumentAppService;
import online.yudream.base.application.platform.graph.service.GraphAppService;
import online.yudream.base.application.platform.integration.service.IntegrationAppService;
import online.yudream.base.interfaces.platform.dataviz.controller.ChartDataController;
import online.yudream.base.interfaces.platform.document.controller.WordDocumentController;
import online.yudream.base.interfaces.platform.graph.controller.GraphController;
import online.yudream.base.interfaces.platform.integration.controller.IntegrationController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LifecycleEnableControllerTest {

    @Test
    void exposesWordTemplateEnableAction() throws Exception {
        WordDocumentAppService service = mock(WordDocumentAppService.class);
        new WordDocumentController(service).enableTemplate(1L);

        verify(service).enableTemplate(1L);
        assertPostPath(WordDocumentController.class, "enableTemplate", "/word-templates/{id}/enable");
    }

    @Test
    void exposesGraphConnectionEnableAction() throws Exception {
        GraphAppService service = mock(GraphAppService.class);
        new GraphController(service).enableConnection(2L);

        verify(service).enableConnection(2L);
        assertPostPath(GraphController.class, "enableConnection", "/connections/{id}/enable");
    }

    @Test
    void exposesIntegrationEnableActions() throws Exception {
        IntegrationAppService service = mock(IntegrationAppService.class);
        IntegrationController controller = new IntegrationController(service);

        controller.enableConnector(3L);
        controller.enableScript(4L);

        verify(service).enableConnector(3L);
        verify(service).enableScript(4L);
        assertPostPath(IntegrationController.class, "enableConnector", "/http-connectors/{id}/enable");
        assertPostPath(IntegrationController.class, "enableScript", "/runtime-scripts/{id}/enable");
    }

    @Test
    void exposesChartDefinitionEnableAction() throws Exception {
        ChartAppService service = mock(ChartAppService.class);
        new ChartDataController(service).enableDefinition(5L);

        verify(service).enableDefinition(5L);
        assertPostPath(ChartDataController.class, "enableDefinition", "/definitions/{id}/enable");
    }

    private void assertPostPath(Class<?> controllerType, String methodName, String expectedPath) throws Exception {
        Method method = controllerType.getMethod(methodName, Long.class);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(expectedPath);
    }
}
