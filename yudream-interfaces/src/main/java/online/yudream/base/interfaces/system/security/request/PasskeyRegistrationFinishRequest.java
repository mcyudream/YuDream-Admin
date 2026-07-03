package online.yudream.base.interfaces.system.security.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasskeyRegistrationFinishRequest {

    private String deviceName;

    @NotBlank(message = "Passkey 注册请求不能为空")
    private String requestJson;

    @NotBlank(message = "Passkey 注册响应不能为空")
    private String responseJson;
}
