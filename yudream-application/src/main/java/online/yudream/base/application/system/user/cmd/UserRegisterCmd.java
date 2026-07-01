package online.yudream.base.application.system.user.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRegisterCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String email;
    private String password;
    private String nickname;
}
