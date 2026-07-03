package online.yudream.base.interfaces.system.user.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PasskeyAuthenticationOptionsRes {

    private String requestJson;

    private String publicKeyJson;
}
