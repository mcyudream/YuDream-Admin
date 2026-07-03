package online.yudream.base.domain.system.security.service;

import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;
import online.yudream.base.domain.system.security.valobj.OAuthClientToken;
import online.yudream.base.domain.system.security.valobj.OAuthClientUserInfo;

public interface OAuthClientGateway {

    OAuthClientToken exchangeCode(OAuthProviderRegistration provider, String code, String state);

    OAuthClientUserInfo userInfo(OAuthProviderRegistration provider, OAuthClientToken token);
}
