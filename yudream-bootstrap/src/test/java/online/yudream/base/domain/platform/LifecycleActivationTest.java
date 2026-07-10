package online.yudream.base.domain.platform;

import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.document.aggregate.WordTemplate;
import online.yudream.base.domain.platform.document.enumerate.TemplateStatus;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.HttpMethodType;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleActivationTest {

    @Test
    void activatesWordTemplateWithoutChangingConfiguration() {
        WordTemplate template = WordTemplate.builder()
                .name("Contract")
                .code("contract")
                .templateFileId(12L)
                .originalFilename("contract.docx")
                .placeholders(Map.of("name", "Name"))
                .description("Contract template")
                .status(TemplateStatus.DISABLED)
                .build();

        template.activate();

        assertThat(template.getStatus()).isEqualTo(TemplateStatus.ACTIVE);
        assertThat(template.getTemplateFileId()).isEqualTo(12L);
        assertThat(template.getPlaceholders()).containsEntry("name", "Name");
        assertThat(template.getDescription()).isEqualTo("Contract template");
    }

    @Test
    void activatesGraphConnectionWithoutChangingConfiguration() {
        GraphConnection connection = GraphConnection.builder()
                .name("Main graph")
                .code("main")
                .uri("bolt://graph:7687")
                .username("neo4j")
                .password("secret")
                .database("yudream")
                .status(GraphConnectionStatus.DISABLED)
                .build();

        connection.activate();

        assertThat(connection.getStatus()).isEqualTo(GraphConnectionStatus.ACTIVE);
        assertThat(connection.getUri()).isEqualTo("bolt://graph:7687");
        assertThat(connection.getPassword()).isEqualTo("secret");
        assertThat(connection.getDatabase()).isEqualTo("yudream");
    }

    @Test
    void activatesHttpConnectorWithoutChangingConfiguration() {
        HttpConnector connector = HttpConnector.builder()
                .name("Orders")
                .code("orders")
                .url("https://example.test/orders")
                .method(HttpMethodType.POST)
                .headers(Map.of("Authorization", "Bearer token"))
                .queryParams(Map.of("tenant", "default"))
                .bodyTemplate("{\"id\":1}")
                .timeoutMillis(5000)
                .retryTimes(2)
                .status(ConnectorStatus.DISABLED)
                .build();

        connector.activate();

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.ACTIVE);
        assertThat(connector.getUrl()).isEqualTo("https://example.test/orders");
        assertThat(connector.getHeaders()).containsEntry("Authorization", "Bearer token");
        assertThat(connector.getBodyTemplate()).isEqualTo("{\"id\":1}");
        assertThat(connector.getRetryTimes()).isEqualTo(2);
    }

    @Test
    void activatesRuntimeScriptWithoutChangingConfiguration() {
        RuntimeScript script = RuntimeScript.builder()
                .name("Sync")
                .code("sync")
                .language(RuntimeLanguage.PYTHON)
                .scriptContent("print('sync')")
                .timeoutMillis(9000)
                .env(Map.of("MODE", "safe"))
                .status(ConnectorStatus.DISABLED)
                .build();

        script.activate();

        assertThat(script.getStatus()).isEqualTo(ConnectorStatus.ACTIVE);
        assertThat(script.getScriptContent()).isEqualTo("print('sync')");
        assertThat(script.getTimeoutMillis()).isEqualTo(9000);
        assertThat(script.getEnv()).containsEntry("MODE", "safe");
    }

    @Test
    void activatesChartDefinitionWithoutChangingConfiguration() {
        ChartDefinition chart = ChartDefinition.builder()
                .code("sales")
                .name("Sales")
                .chartType(ChartType.BAR)
                .dataSource("mongo")
                .enabled(false)
                .build();

        chart.activate();

        assertThat(chart.isEnabled()).isTrue();
        assertThat(chart.getDataSource()).isEqualTo("mongo");
        assertThat(chart.getChartType()).isEqualTo(ChartType.BAR);
    }
}
