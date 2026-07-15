package online.yudream.base.infra.platform.agent.service;

import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginAgentApplicationRegistryTest {

    @Test
    void removesRuntimeAgentWhenPluginRegistrationIsClosed() throws Exception {
        PluginAgentApplicationRegistry registry = new PluginAgentApplicationRegistry();
        AgentApplication application = AgentApplication.builder()
                .code("plugin-agent")
                .name("插件 Agent")
                .workflowJson("{}")
                .toolCodes(List.of())
                .status(AgentApplicationStatus.PUBLISHED)
                .build();

        AutoCloseable registration = registry.register("sample-plugin", application);

        assertThat(registry.findByCode("plugin-agent")).containsSame(application);
        assertThat(registry.applications()).containsExactly(application);

        registration.close();

        assertThat(registry.findByCode("plugin-agent")).isEmpty();
        assertThat(registry.applications()).isEmpty();
    }
}
