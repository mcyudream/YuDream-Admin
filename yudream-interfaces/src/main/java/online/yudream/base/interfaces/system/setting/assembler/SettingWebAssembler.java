package online.yudream.base.interfaces.system.setting.assembler;

import online.yudream.base.application.system.setting.cmd.SiteSettingUpdateCmd;
import online.yudream.base.application.system.setting.cmd.ThemeSettingUpdateCmd;
import online.yudream.base.application.system.setting.dto.SiteSettingDTO;
import online.yudream.base.application.system.setting.dto.ThemeSettingDTO;
import online.yudream.base.interfaces.system.setting.request.SiteSettingUpdateRequest;
import online.yudream.base.interfaces.system.setting.request.ThemeSettingUpdateRequest;
import online.yudream.base.interfaces.system.setting.res.SiteSettingRes;
import online.yudream.base.interfaces.system.setting.res.ThemeSettingRes;

public class SettingWebAssembler {

    public static SiteSettingUpdateCmd toCmd(SiteSettingUpdateRequest request) {
        SiteSettingUpdateCmd cmd = new SiteSettingUpdateCmd();
        cmd.setSiteName(request.getSiteName());
        cmd.setSiteDescription(request.getSiteDescription());
        cmd.setCopyrightCompany(request.getCopyrightCompany());
        cmd.setCopyrightWebsite(request.getCopyrightWebsite());
        cmd.setCopyrightDates(request.getCopyrightDates());
        return cmd;
    }

    public static ThemeSettingUpdateCmd toCmd(ThemeSettingUpdateRequest request) {
        ThemeSettingUpdateCmd cmd = new ThemeSettingUpdateCmd();
        cmd.setConfig(request.getConfig());
        return cmd;
    }

    public static SiteSettingRes toRes(SiteSettingDTO dto) {
        return SiteSettingRes.builder()
                .siteName(dto.getSiteName())
                .siteDescription(dto.getSiteDescription())
                .logo(dto.getLogo())
                .favicon(dto.getFavicon())
                .copyrightCompany(dto.getCopyrightCompany())
                .copyrightWebsite(dto.getCopyrightWebsite())
                .copyrightDates(dto.getCopyrightDates())
                .build();
    }

    public static ThemeSettingRes toRes(ThemeSettingDTO dto) {
        return ThemeSettingRes.builder()
                .config(dto.getConfig())
                .build();
    }
}
