package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiKeyCreateResultRes {
    private ApiKeyCredentialRes credential;
    private String plaintext;
}
