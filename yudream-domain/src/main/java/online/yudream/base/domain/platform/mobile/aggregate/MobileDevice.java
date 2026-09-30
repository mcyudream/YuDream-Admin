package online.yudream.base.domain.platform.mobile.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.domain.platform.mobile.valobj.MobilePushChannel;

import java.time.LocalDateTime;

/**
 * 移动设备注册记录：绑定当前登录用户，按 (userId, pushToken) 幂等 upsert。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MobileDevice extends BaseDomain {

    private Long userId;
    private MobilePlatform platform;
    private String pushChannel;
    private String pushToken;
    private String deviceName;
    private String appVersion;
    private String hostVersion;
    private LocalDateTime registeredAt;
    private LocalDateTime lastSeenAt;
    /** 逻辑删除标记：注销后保留记录供审计，查询侧按 false 过滤。 */
    private Boolean deleted;

    public static MobileDevice register(Long userId,
                                        MobilePlatform platform,
                                        String pushChannel,
                                        String pushToken,
                                        String deviceName,
                                        String appVersion,
                                        String hostVersion) {
        if (userId == null) {
            throw new BizException("移动设备必须绑定登录用户");
        }
        if (platform == null) {
            throw new BizException("移动设备平台仅支持 android/ios");
        }
        if (pushToken == null || pushToken.isBlank()) {
            throw new BizException("推送 token 不能为空");
        }
        String safeChannel = MobilePushChannel.of(pushChannel).value();
        LocalDateTime now = LocalDateTime.now();
        return MobileDevice.builder()
                .userId(userId)
                .platform(platform)
                .pushChannel(safeChannel)
                .pushToken(pushToken.trim())
                .deviceName(normalize(deviceName))
                .appVersion(normalize(appVersion))
                .hostVersion(normalize(hostVersion))
                .registeredAt(now)
                .lastSeenAt(now)
                .deleted(false)
                .build();
    }

    /**
     * 同一 (userId, pushToken) 再次注册时刷新设备信息，注册时间保持首次值；平台变化是合法场景（换机重装）。
     */
    public void refresh(MobilePlatform platform, String pushChannel, String deviceName,
                        String appVersion, String hostVersion) {
        if (platform != null) {
            this.platform = platform;
        }
        if (pushChannel != null && !pushChannel.isBlank()) {
            this.pushChannel = MobilePushChannel.of(pushChannel).value();
        }
        this.deviceName = normalize(deviceName);
        this.appVersion = normalize(appVersion);
        this.hostVersion = normalize(hostVersion);
        this.lastSeenAt = LocalDateTime.now();
        this.deleted = false;
    }

    public void unregister() {
        this.deleted = true;
    }

    public boolean belongsTo(Long userId) {
        if (userId == null || !userId.equals(this.userId)) {
            throw new BizException("无权操作该设备");
        }
        return true;
    }

    public boolean deleted() {
        return Boolean.TRUE.equals(deleted);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
