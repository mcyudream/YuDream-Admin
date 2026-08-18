package online.yudream.base.interfaces.platform.chat.assembler;

import online.yudream.base.interfaces.platform.chat.request.ChatSendRequest;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport.SecurityPrincipal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatWebAssemblerPermissionTest {

    @Test
    void sendCopiesImmutablePermissionsFromAuthenticatedPrincipal() {
        ChatSendRequest request = new ChatSendRequest();
        request.setQuestion("question");
        List<String> permissions = new ArrayList<>(List.of("platform:wiki:search"));

        var command = ChatWebAssembler.send(request, new SecurityPrincipal(7L, permissions));
        permissions.clear();

        assertThat(command.getPermissionCodes()).containsExactly("platform:wiki:search");
        assertThatThrownBy(() -> command.getPermissionCodes().add("platform:wiki:edit"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sendUsesImmutableEmptySnapshotWhenPrincipalHasNoPermissions() {
        ChatSendRequest request = new ChatSendRequest();
        request.setQuestion("question");

        var command = ChatWebAssembler.send(request, new SecurityPrincipal(7L, null));

        assertThat(command.getPermissionCodes()).isEmpty();
        assertThatThrownBy(() -> command.getPermissionCodes().add("platform:wiki:search"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
