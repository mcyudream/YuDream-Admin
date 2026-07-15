package online.yudream.base.interfaces.platform.agent.assembler;

import online.yudream.base.interfaces.platform.agent.request.AgentAttachmentRequest;
import online.yudream.base.interfaces.platform.agent.request.AgentRunRequest;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport.SecurityPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunAttachmentAssemblerTest {

    @Test
    void shouldMapStructuredDebugAttachments() {
        AgentAttachmentRequest attachment = new AgentAttachmentRequest();
        attachment.setName("manual.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSize(1024L);
        attachment.setDataUrl("data:application/pdf;base64,JVBERg==");
        AgentRunRequest request = new AgentRunRequest();
        request.setInput("总结文档");
        request.setAttachments(List.of(attachment));

        var command = AgentWebAssembler.toRunCmd(
                12L,
                request,
                new SecurityPrincipal(7L, List.of("platform:agent:run"))
        );

        assertThat(command.getAttachments()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("manual.pdf");
            assertThat(item.contentType()).isEqualTo("application/pdf");
            assertThat(item.dataUrl()).startsWith("data:application/pdf;base64,");
        });
        assertThat(command.isPermissionContextExplicit()).isTrue();
        assertThat(command.getPermissionCodes()).containsExactly("platform:agent:run");
    }
}
