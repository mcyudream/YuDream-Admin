package online.yudream.base.infra.platform.plugin.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PluginAgentManifestReaderTest {

    @Test
    void readsStaticAgentWorkflowContributionWithoutPersistenceFields() {
        var definitions = new PluginAgentManifestReader().read(new ByteArrayInputStream("""
                name: sample
                main: sample.Plugin
                version: 1.0.0
                agents:
                  - code: sample-agent
                    name: 示例 Agent
                    description: 运行时应用
                    systemPrompt: 你是示例 Agent
                    workflow: agents/sample.json
                    tools: [web.fetch]
                """.getBytes(StandardCharsets.UTF_8)));

        assertThat(definitions).singleElement().satisfies(definition -> {
            assertThat(definition.code()).isEqualTo("sample-agent");
            assertThat(definition.workflowResource()).isEqualTo("agents/sample.json");
            assertThat(definition.toolCodes()).containsExactly("web.fetch");
        });
    }
}
