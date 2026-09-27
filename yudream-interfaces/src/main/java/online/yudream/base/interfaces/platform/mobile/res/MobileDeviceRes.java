package online.yudream.base.interfaces.platform.mobile.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 当前用户移动设备响应。id 为 Snowflake Long 的字符串形态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileDeviceRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String platform;
    private String pushChannel;
    private String pushToken;
    private String deviceName;
    private String appVersion;
    private String hostVersion;
    private LocalDateTime registeredAt;
    private LocalDateTime lastSeenAt;
}
