package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {

    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String qq;
}
