package online.yudream.base.domain.system.security.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.valobj.TokenPolicy;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSecurityPolicy extends BaseDomain {

    public static final String DEFAULT_CODE = "default";

    private String code;
    private boolean apiEncryptionEnabled;
    private boolean dualTokenEnabled;
    private boolean apiKeyEnabled;
    private boolean passkeyEnabled;
    private boolean oauthServerEnabled;
    private boolean oauthClientEnabled;
    /** 开放注册总开关：关闭后 /register 一律拒绝，账号仅能由管理员开户或经第三方登录绑定已有账号。 */
    private boolean registrationEnabled = true;
    private TokenPolicy tokenPolicy;

    public static ApiSecurityPolicy createDefault() {
        return ApiSecurityPolicy.builder()
                .code(DEFAULT_CODE)
                .apiEncryptionEnabled(false)
                .dualTokenEnabled(false)
                .apiKeyEnabled(false)
                .passkeyEnabled(false)
                .oauthServerEnabled(false)
                .oauthClientEnabled(false)
                .registrationEnabled(true)
                .tokenPolicy(TokenPolicy.defaultPolicy())
                .build();
    }

    public void updateSwitches(boolean apiEncryptionEnabled,
                               boolean dualTokenEnabled,
                               boolean apiKeyEnabled,
                               boolean passkeyEnabled,
                               boolean oauthServerEnabled,
                               boolean oauthClientEnabled,
                               boolean registrationEnabled) {
        this.apiEncryptionEnabled = apiEncryptionEnabled;
        this.dualTokenEnabled = dualTokenEnabled;
        this.apiKeyEnabled = apiKeyEnabled;
        this.passkeyEnabled = passkeyEnabled;
        this.oauthServerEnabled = oauthServerEnabled;
        this.oauthClientEnabled = oauthClientEnabled;
        this.registrationEnabled = registrationEnabled;
    }

    public void updateTokenPolicy(TokenPolicy tokenPolicy) {
        if (tokenPolicy == null) {
            throw new BizException("令牌策略不能为空");
        }
        this.tokenPolicy = tokenPolicy;
    }
}
