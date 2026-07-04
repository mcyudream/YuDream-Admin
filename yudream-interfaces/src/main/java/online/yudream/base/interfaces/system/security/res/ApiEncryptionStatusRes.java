package online.yudream.base.interfaces.system.security.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiEncryptionStatusRes {
    private boolean enabled;
    private String algorithm;
}
