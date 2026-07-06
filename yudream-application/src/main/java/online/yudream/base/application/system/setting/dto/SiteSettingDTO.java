package online.yudream.base.application.system.setting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSettingDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String siteName;
    private String siteDescription;
    private String logo;
    private String favicon;
    private String loginBanner;
    private String copyrightCompany;
    private String copyrightWebsite;
    private String copyrightDates;
}
