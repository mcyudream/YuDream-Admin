package online.yudream.base.interfaces.system.user.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasskeyAuthenticationFinishRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "Passkey 登录请求不能为空")
    private String requestJson;

    @NotBlank(message = "Passkey 登录响应不能为空")
    private String responseJson;
}
