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
    private boolean enabled;
    private String supportedTypes;

    public void update(String name, String appId, String appKey, String callbackUrl, boolean enabled, String supportedTypes) {
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            throw new BizException("第三方登录 AppId 和 AppKey 不能为空");
        }
        this.name = name == null || name.isBlank() ? "Wwoyun 登录" : name.trim();
        this.appId = appId.trim();
        this.appKey = appKey.trim();
        this.callbackUrl = callbackUrl == null ? null : callbackUrl.trim();
        this.enabled = enabled;
        this.supportedTypes = supportedTypes == null || supportedTypes.isBlank() ? "qq,wx,google,gitee,github" : supportedTypes;
    }
}
