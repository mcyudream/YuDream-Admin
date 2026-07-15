package online.yudream.base.interfaces.platform.ai.assembler;

import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport.SecurityPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiWebAssemblerPermissionSnapshotTest {

    @Test
    void shouldCaptureCmsCallerPermissionsBeforeAsyncExecution() {
        CmsPageGenerateRequest request = new CmsPageGenerateRequest();
        request.setPrompt("Build a page");

        var command = AiWebAssembler.toCmd(
                request,
                new SecurityPrincipal(7L, List.of("platform:cms:edit", "platform:ai:generate"))
        );

        assertThat(command.isPermissionContextExplicit()).isTrue();
        assertThat(command.getPermissionCodes())
                .containsExactly("platform:cms:edit", "platform:ai:generate");
    }
}
