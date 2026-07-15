package online.yudream.base.infra.platform.ai.service;

import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiCompatibleGenerationGatewayTest {

    @Test
    void shouldUseExactScopedToolInstancesInsteadOfGlobalBeans() {
        CountingTool scopedTool = new CountingTool("shared.tool");
        CountingTool globalToolWithSameName = new CountingTool("shared.tool");
        CountingTool unrelatedGlobalTool = new CountingTool("global.only");
        OpenAiCompatibleGenerationGateway gateway = gatewayWithGlobalTools(
                globalToolWithSameName,
                unrelatedGlobalTool
        );

        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(scopedTool))) {
            assertThat(AiAgentToolExecutionScope.currentTools()).containsExactly(scopedTool);

            List<ToolCallback> callbacks = toolCallbacks(gateway);

            assertThat(callbacks)
                    .extracting(callback -> callback.getToolDefinition().name())
                    .containsExactly("shared_tool");
            callbacks.getFirst().call("{}");
        }

        assertThat(scopedTool.executions()).isOne();
        assertThat(globalToolWithSameName.executions()).isZero();
        assertThat(unrelatedGlobalTool.executions()).isZero();
        assertThat(AiAgentToolExecutionScope.currentTools()).isNull();
    }

    @Test
    void shouldPreferExactScopedToolsWhenPluginScopeAlsoExists() {
        CountingTool scopedTool = new CountingTool("model.tool");
        OpenAiCompatibleGenerationGateway gateway = gatewayWithGlobalTools();
        PluginAiToolExecutionScope.set(mock(PluginAiExecutionContext.class));

        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(scopedTool))) {
            assertThat(toolCallbacks(gateway))
                    .extracting(callback -> callback.getToolDefinition().name())
                    .containsExactly("model_tool");
        } finally {
            PluginAiToolExecutionScope.clear();
        }
    }

    @Test
    void shouldRestoreOuterScopeAndCloseIdempotently() {
        CountingTool outerTool = new CountingTool("outer.tool");
        CountingTool innerTool = new CountingTool("inner.tool");
        AiAgentToolExecutionScope outer = AiAgentToolExecutionScope.open(List.of(outerTool));
        AiAgentToolExecutionScope inner = AiAgentToolExecutionScope.open(List.of(innerTool));

        try {
            assertThat(AiAgentToolExecutionScope.currentTools()).containsExactly(innerTool);

            inner.close();
            assertThat(AiAgentToolExecutionScope.currentTools()).containsExactly(outerTool);

            inner.close();
            assertThat(AiAgentToolExecutionScope.currentTools()).containsExactly(outerTool);

            outer.close();
            outer.close();
            assertThat(AiAgentToolExecutionScope.currentTools()).isNull();
        } finally {
            inner.close();
            outer.close();
        }
    }

    @Test
    void shouldPreserveToolModeWhenCopyingToolCallingFlag() {
        for (AiToolMode mode : AiToolMode.values()) {
            AiGenerationRequest request = new AiGenerationRequest(
                    "system",
                    "user",
                    null,
                    "provider",
                    "model",
                    Map.of(),
                    List.of(),
                    true,
                    mode
            );

            assertThat(request.withToolCallingEnabled(false).toolMode()).isEqualTo(mode);
            assertThat(request.withToolCallingEnabled(true).toolMode()).isEqualTo(mode);
        }
    }

    @Test
    void shouldPassCompleteCustomToolJsonSchemaToTheModel() {
        OpenAiCompatibleGenerationGateway gateway = gatewayWithGlobalTools();
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(
                "risk_score",
                "Risk score",
                "Calculate risk",
                "risk:score",
                "Risk score",
                "Agent",
                "Calculate risk",
                Map.of(),
                Map.of(
                        "type", "object",
                        "properties", Map.of("score", Map.of("type", "integer")),
                        "required", List.of("score"),
                        "additionalProperties", false
                )
        );

        String schema = inputSchema(gateway, descriptor);

        assertThat(schema).contains("\"required\":[\"score\"]", "\"additionalProperties\":false", "\"type\":\"integer\"");
    }

    @Test
    void shouldPassAnEmptyCompleteCustomToolJsonSchemaToTheModel() {
        OpenAiCompatibleGenerationGateway gateway = gatewayWithGlobalTools();
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(
                "empty_schema",
                "Empty schema",
                "Accept an empty schema",
                "test:tool",
                "Empty schema",
                "Agent",
                "Accept an empty schema",
                Map.of("legacy", "must not be used"),
                Map.of()
        );

        assertThat(inputSchema(gateway, descriptor)).isEqualTo("{}");
    }

    @SuppressWarnings("unchecked")
    private OpenAiCompatibleGenerationGateway gatewayWithGlobalTools(AiAgentTool... tools) {
        ObjectProvider<AiAgentTool> provider = mock(ObjectProvider.class);
        PluginAiToolRegistry pluginToolRegistry = mock(PluginAiToolRegistry.class);
        when(provider.stream()).thenAnswer(ignored -> Stream.of(tools));
        when(pluginToolRegistry.tools()).thenReturn(List.of());
        return new OpenAiCompatibleGenerationGateway(
                provider,
                mock(AiProviderConfigParser.class),
                List.of(),
                new AiClientProperties(),
                pluginToolRegistry
        );
    }

    @SuppressWarnings("unchecked")
    private List<ToolCallback> toolCallbacks(OpenAiCompatibleGenerationGateway gateway) {
        return (List<ToolCallback>) ReflectionTestUtils.invokeMethod(
                gateway,
                "toolCallbacks",
                new ArrayList<AiAgentToolResult>(),
                null,
                null
        );
    }

    private String inputSchema(OpenAiCompatibleGenerationGateway gateway, AiAgentToolDescriptor descriptor) {
        return (String) ReflectionTestUtils.invokeMethod(gateway, "inputSchema", descriptor);
    }

    private static final class CountingTool implements AiAgentTool {

        private final String name;
        private final AtomicInteger executions = new AtomicInteger();

        private CountingTool(String name) {
            this.name = name;
        }

        @Override
        public AiAgentToolDescriptor descriptor() {
            return new AiAgentToolDescriptor(
                    name,
                    name,
                    "test tool",
                    "test:tool",
                    "test tool",
                    "test",
                    "test tool",
                    Map.of()
            );
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            executions.incrementAndGet();
            return new AiAgentToolResult(name, "test", "test:tool", "ok", Map.of());
        }

        private int executions() {
            return executions.get();
        }
    }
}
