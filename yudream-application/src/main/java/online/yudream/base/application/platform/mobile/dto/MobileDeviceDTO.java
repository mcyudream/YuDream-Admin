package online.yudream.base.application.platform.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 当前用户的移动设备视图。Snowflake ID 按约定以 string 下发，避免 JS 精度丢失。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileDeviceDTO implements Serializable {

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
