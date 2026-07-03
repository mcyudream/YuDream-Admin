package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasskeyAuthenticationStartRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;
}
