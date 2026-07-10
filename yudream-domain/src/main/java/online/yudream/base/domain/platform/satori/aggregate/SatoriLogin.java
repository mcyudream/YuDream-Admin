package online.yudream.base.domain.platform.satori.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SatoriLogin extends BaseDomain {

    private Long connectionId;
    private String platform;
    private String userId;
    private SatoriLoginStatus status;
    private String adapter;
    private List<String> features;

    public static SatoriLogin create(Long connectionId, String platform, String userId, SatoriLoginStatus status,
                                     String adapter, List<String> features) {
        SatoriLogin login = new SatoriLogin();
        login.connectionId = required(connectionId, "连接 ID 不能为空");
        login.platform = required(platform, "平台不能为空");
        login.userId = required(userId, "登录账号不能为空");
        login.status = status == null ? SatoriLoginStatus.OFFLINE : status;
        login.adapter = adapter == null ? null : adapter.trim();
        login.features = features == null ? List.of() : List.copyOf(features);
        return login;
    }

    public void refresh(SatoriLoginStatus status, String adapter, List<String> features) {
        this.status = status == null ? SatoriLoginStatus.OFFLINE : status;
        this.adapter = adapter == null ? null : adapter.trim();
        this.features = features == null ? List.of() : List.copyOf(features);
    }

    public String naturalKey() {
        return connectionId + ":" + platform + ":" + userId;
    }

    private static Long required(Long value, String message) {
        if (value == null) {
            throw new BizException(message);
        }
        return value;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
