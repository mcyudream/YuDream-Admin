package online.yudream.base.domain.system.security.service;

import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;
import online.yudream.base.domain.system.security.valobj.PasskeyAuthenticationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyAuthenticationResult;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationResult;
import online.yudream.base.domain.system.security.valobj.PasskeyRelyingPartyContext;

import java.util.List;

public interface PasskeyCeremonyGateway {

    PasskeyRegistrationOptions startRegistration(
            PasskeyRelyingPartyContext relyingParty,
            Long userId,
            String username,
            String displayName,
            List<PasskeyCredential> existingCredentials
    );

    PasskeyRegistrationResult finishRegistration(PasskeyRelyingPartyContext relyingParty, String requestJson, String responseJson);

    PasskeyAuthenticationOptions startAuthentication(PasskeyRelyingPartyContext relyingParty, String username);

    PasskeyAuthenticationResult finishAuthentication(PasskeyRelyingPartyContext relyingParty, String requestJson, String responseJson);
}
