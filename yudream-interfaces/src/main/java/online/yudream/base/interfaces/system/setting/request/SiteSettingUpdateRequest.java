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
}
