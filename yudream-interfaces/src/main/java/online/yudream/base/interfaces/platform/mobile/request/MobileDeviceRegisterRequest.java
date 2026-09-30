package online.yudream.base.interfaces.platform.mobile.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class MobileDeviceRegisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** android | ios */
    @NotBlank(message = "移动设备平台仅支持 android/ios")
    private String platform;
    /** fcm | apns | xiaomi/huawei/oppo/vivo 等厂商通道 */
    @NotBlank(message = "推送通道不能为空")
    private String pushChannel;
    @NotBlank(message = "推送 token 不能为空")
    private String pushToken;
    private String deviceName;
    private String appVersion;
    private String hostVersion;
}
