package online.yudream.base.application.platform.mobile.cmd;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileDeviceRegisterCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private MobilePlatform platform;
    private String pushChannel;
    private String pushToken;
    private String deviceName;
    private String appVersion;
    private String hostVersion;
}
