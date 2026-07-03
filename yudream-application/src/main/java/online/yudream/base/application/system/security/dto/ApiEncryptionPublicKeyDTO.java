package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class ApiEncryptionPublicKeyDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean enabled;
    private String algorithm;
    private String publicKey;
}
