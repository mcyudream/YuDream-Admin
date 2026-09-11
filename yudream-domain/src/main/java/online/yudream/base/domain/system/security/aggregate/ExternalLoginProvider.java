package online.yudream.base.domain.system.security.aggregate;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExternalLoginProvider extends BaseDomain {
    private String code;
    private String name;
    private String protocol;
    private String appId;
    private String appKey;
    private String callbackUrl;
    private String endpoint;
    private boolean enabled;
    private String supportedTypes;

    public static final String DEFAULT_CODE = "wwoyun";
    public static final String DEFAULT_PROTOCOL = "WWOYUN";
    public static final String DEFAULT_NAME = "Wwoyun 登录";
    public static final String DEFAULT_TYPES = "qq,wx,google,gitee,github";
    public static final String DEFAULT_ENDPOINT = "https://login.wwoyun.cn/connect.php";

    public void update(String name, String appId, String appKey, String callbackUrl, String endpoint, boolean enabled, String supportedTypes) {
        if (enabled) {
            if (appId == null || appId.isBlank()) {
                throw new BizException("第三方登录 AppId 不能为空");
            }
            boolean missingSecret = (appKey == null || appKey.isBlank()) && (this.appKey == null || this.appKey.isBlank());
            if (missingSecret) {
                throw new BizException("第三方登录 AppKey 不能为空");
            }
        }
        this.name = name == null || name.isBlank() ? fallbackName() : name.trim();
        if (appId != null && !appId.isBlank()) {
            this.appId = appId.trim();
        }
        if (appKey != null && !appKey.isBlank()) {
            this.appKey = appKey.trim();
        }
        this.callbackUrl = callbackUrl == null ? null : callbackUrl.trim();
        this.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint.trim();
        this.enabled = enabled;
        this.supportedTypes = supportedTypes == null || supportedTypes.isBlank() ? DEFAULT_TYPES : supportedTypes;
        if (this.protocol == null || this.protocol.isBlank()) {
            this.protocol = DEFAULT_PROTOCOL;
        }
    }

    public String resolvedEndpoint() {
        return endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint;
    }

    private String fallbackName() {
        return DEFAULT_CODE.equalsIgnoreCase(code) ? DEFAULT_NAME : (code == null || code.isBlank() ? DEFAULT_NAME : code);
    }
}
