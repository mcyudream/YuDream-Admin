package online.yudream.base.application.system.user.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserCreateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String qq;
    private String password;
    private String encodedPassword;
    private boolean emailVerified;
    private List<Long> roleIds = new ArrayList<>();
    private List<UserDeptAssignCmd> depts = new ArrayList<>();
}
