package online.yudream.base.domain.system.security.service;

import online.yudream.base.domain.system.security.aggregate.ExternalLoginProvider;

public interface ExternalLoginGateway {
    String authorizationUrl(ExternalLoginProvider provider, String platformType, String state);
    ExternalIdentity exchange(ExternalLoginProvider provider, String platformType, String code);

    record ExternalIdentity(String socialUid, String nickname, String avatarUrl, String gender, String location) { }
}
