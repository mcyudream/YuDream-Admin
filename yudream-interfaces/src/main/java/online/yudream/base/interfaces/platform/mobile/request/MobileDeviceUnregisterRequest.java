package online.yudream.base.interfaces.platform.mobile.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class MobileDeviceUnregisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "推送 token 不能为空")
    private String pushToken;
}
