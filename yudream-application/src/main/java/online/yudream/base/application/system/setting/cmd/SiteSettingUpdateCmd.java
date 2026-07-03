package online.yudream.base.application.system.setting.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SiteSettingUpdateCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String siteName;
    private String siteDescription;
    private String copyrightCompany;
    private String copyrightWebsite;
    private String copyrightDates;
}
