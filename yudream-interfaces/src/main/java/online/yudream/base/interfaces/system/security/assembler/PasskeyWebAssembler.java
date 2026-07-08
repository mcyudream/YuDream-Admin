package online.yudream.base.interfaces.system.security.assembler;

import online.yudream.base.application.system.security.cmd.PasskeyAuthenticationFinishCmd;
import online.yudream.base.application.system.security.cmd.PasskeyAuthenticationStartCmd;
import online.yudream.base.application.system.security.cmd.PasskeyRegistrationFinishCmd;
import online.yudream.base.application.system.security.cmd.PasskeyRegistrationStartCmd;
import online.yudream.base.domain.system.security.valobj.PasskeyRelyingPartyContext;
import online.yudream.base.interfaces.system.security.request.PasskeyRegistrationFinishRequest;
import online.yudream.base.interfaces.system.user.request.PasskeyAuthenticationFinishRequest;
import online.yudream.base.interfaces.system.user.request.PasskeyAuthenticationStartRequest;

public class PasskeyWebAssembler {

    private PasskeyWebAssembler() {
    }

    public static PasskeyRegistrationStartCmd toRegistrationStartCmd(Long userId, PasskeyRelyingPartyContext relyingParty) {
        PasskeyRegistrationStartCmd cmd = new PasskeyRegistrationStartCmd();
        cmd.setUserId(userId);
        cmd.setRelyingParty(relyingParty);
        return cmd;
    }

    public static PasskeyRegistrationFinishCmd toRegistrationFinishCmd(
            Long userId,
            PasskeyRegistrationFinishRequest request,
            PasskeyRelyingPartyContext relyingParty
    ) {
        PasskeyRegistrationFinishCmd cmd = new PasskeyRegistrationFinishCmd();
        cmd.setUserId(userId);
        cmd.setDeviceName(request.getDeviceName());
        cmd.setRequestJson(request.getRequestJson());
        cmd.setResponseJson(request.getResponseJson());
        cmd.setRelyingParty(relyingParty);
        return cmd;
    }

    public static PasskeyAuthenticationStartCmd toAuthenticationStartCmd(
            PasskeyAuthenticationStartRequest request,
            PasskeyRelyingPartyContext relyingParty
    ) {
        PasskeyAuthenticationStartCmd cmd = new PasskeyAuthenticationStartCmd();
        cmd.setUsername(request.getUsername());
        cmd.setRelyingParty(relyingParty);
        return cmd;
    }

    public static PasskeyAuthenticationFinishCmd toAuthenticationFinishCmd(
            PasskeyAuthenticationFinishRequest request,
            PasskeyRelyingPartyContext relyingParty
    ) {
        PasskeyAuthenticationFinishCmd cmd = new PasskeyAuthenticationFinishCmd();
        cmd.setUsername(request.getUsername());
        cmd.setRequestJson(request.getRequestJson());
        cmd.setResponseJson(request.getResponseJson());
        cmd.setRelyingParty(relyingParty);
        return cmd;
    }
}
