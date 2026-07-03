package online.yudream.base.domain.system.security.service;

import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;
import online.yudream.base.domain.system.security.valobj.PasskeyAuthenticationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyAuthenticationResult;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationOptions;
import online.yudream.base.domain.system.security.valobj.PasskeyRegistrationResult;

import java.util.List;

public interface PasskeyCeremonyGateway {

    PasskeyRegistrationOptions startRegistration(Long userId, String username, String displayName, List<PasskeyCredential> existingCredentials);

    PasskeyRegistrationResult finishRegistration(String requestJson, String responseJson);

    PasskeyAuthenticationOptions startAuthentication(String username);

    PasskeyAuthenticationResult finishAuthentication(String requestJson, String responseJson);
}
