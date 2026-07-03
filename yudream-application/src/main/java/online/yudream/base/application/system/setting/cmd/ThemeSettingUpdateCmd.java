package online.yudream.base.application.system.setting.cmd;

import lombok.Data;

import java.util.Map;

@Data
public class ThemeSettingUpdateCmd {

    private Map<String, Object> config;
}
