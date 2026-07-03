package online.yudream.base.application.system.security.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class ApiEncryptedPayloadDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String iv;
    private String data;
}
