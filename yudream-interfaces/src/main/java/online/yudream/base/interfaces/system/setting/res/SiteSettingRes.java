package online.yudream.base.interfaces.system.setting.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSettingRes {

    private String siteName;
    private String siteDescription;
    private String logo;
    private String favicon;
    private String loginBanner;
    private String copyrightCompany;
    private String copyrightWebsite;
    private String copyrightDates;
    /** 登录页默认选中的登录方式；空表示账号密码。取值：password / passkey / external:{providerCode}:{type} */
    private String loginDefaultMethod;
}
