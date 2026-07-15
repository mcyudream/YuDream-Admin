package online.yudream.base.interfaces.platform.agent.assembler;

import online.yudream.base.application.platform.agent.dto.AgentCatalogDTO;
import online.yudream.base.application.platform.agent.dto.AgentKnowledgeSpaceDTO;
import online.yudream.base.application.platform.agent.dto.AgentModelDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCatalogWebAssemblerTest {

    @Test
    void shouldMapSafeWorkflowCatalog() {
        AgentCatalogDTO source = new AgentCatalogDTO(
                List.of(new AgentKnowledgeSpaceDTO("docs", "产品文档", "openai", "embed-3", 8, true, true)),
                List.of(new AgentModelDTO("openai", "OpenAI", "gpt-4.1", "GPT-4.1", "chat", true, true, true))
        );

        var result = AgentWebAssembler.toRes(source);

        assertThat(result.getKnowledgeSpaces()).singleElement().satisfies(space -> {
            assertThat(space.getSlug()).isEqualTo("docs");
            assertThat(space.getTopK()).isEqualTo(8);
        });
        assertThat(result.getModels()).singleElement().satisfies(model -> {
            assertThat(model.getProviderCode()).isEqualTo("openai");
            assertThat(model.isVision()).isTrue();
        });
    }
}
