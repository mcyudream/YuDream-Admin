package online.yudream.base.interfaces.system.setting.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SiteSettingUpdateRequest {

    @NotBlank(message = "站点名称不能为空")
    private String siteName;

    private String siteDescription;

    private String copyrightCompany;

    private String copyrightWebsite;

    private String copyrightDates;

    /** 登录页默认选中的登录方式；空表示账号密码 */
    private String loginDefaultMethod;
}
