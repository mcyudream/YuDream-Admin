package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserUpdateRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String nickname;
    @Email(message = "邮箱格式不正确")
    private String email;
    private String phone;
    private String qq;
    private Boolean emailVerified;
}
