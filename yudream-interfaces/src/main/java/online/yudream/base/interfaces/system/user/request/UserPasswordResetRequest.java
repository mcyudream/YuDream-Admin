package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.interfaces.common.validation.anno.PasswordRule;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserPasswordResetRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "重置令牌不能为空")
    private String token;

    @NotBlank(message = "密码不能为空")
    @PasswordRule
    private String password;
}
