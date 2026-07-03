package online.yudream.base.application.system.security.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PasskeyRevokeCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
}
