package online.yudream.base.domain.system.security.service;

import java.util.Optional;

public interface ExternalLoginTicketStore {
    void saveState(String token, State state);

    Optional<State> consumeState(String token);

    void saveBinding(String token, Binding binding);

    Optional<Binding> consumeBinding(String token);

    record State(String providerCode, String platformType, Long bindUserId) {
    }

    record Binding(String providerCode, String platformType, String socialUid, String nickname,
                   String avatarUrl, String gender, String location) {
    }
}
