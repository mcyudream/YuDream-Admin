package online.yudream.base.application.system.user.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserPasswordResetEmailCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String account;
}
