package online.yudream.base.infra.platform.mobile.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "mobileDevice")
@CompoundIndex(def = "{'userId': 1, 'pushToken': 1}", unique = true)
public class MobileDeviceDO extends BaseDO {

    private Long userId;
    /** android/ios（MobilePlatform 枚举名）。 */
    private String platform;
    /** fcm/apns 或厂商通道小写字符串。 */
    private String pushChannel;
    private String pushToken;
    private String deviceName;
    private String appVersion;
    private String hostVersion;
    private LocalDateTime registeredAt;
    private LocalDateTime lastSeenAt;
    /** 逻辑删除：注销后保留记录，查询侧过滤；缺省 false 兜底存量文档缺字段。 */
    private Boolean deleted = false;

    /** 注销前的有效记录。 */
    public boolean active() {
        return !Boolean.TRUE.equals(deleted);
    }
}
