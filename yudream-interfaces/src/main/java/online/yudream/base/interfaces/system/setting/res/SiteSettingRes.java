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
    private String copyrightCompany;
    private String copyrightWebsite;
    private String copyrightDates;
}
