package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.interfaces.common.validation.anno.PasswordRule;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @NotBlank(message = "用户名或邮箱不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    @PasswordRule
    private String password;
}
