package online.yudream.base.application.system.security.cmd;

import lombok.Data;
import online.yudream.base.domain.system.security.valobj.PasskeyRelyingPartyContext;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PasskeyAuthenticationStartCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private PasskeyRelyingPartyContext relyingParty;
}
