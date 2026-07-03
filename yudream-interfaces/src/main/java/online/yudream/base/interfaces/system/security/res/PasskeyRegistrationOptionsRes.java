package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PasskeyRegistrationOptionsRes {

    private String requestJson;

    private String publicKeyJson;
}
