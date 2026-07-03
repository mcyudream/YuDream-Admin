package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiEncryptedPayloadRes {
    private boolean encrypted;
    private String iv;
    private String data;
}
