package online.yudream.base.application.system.user.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserProfileUpdateCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String qq;
}
