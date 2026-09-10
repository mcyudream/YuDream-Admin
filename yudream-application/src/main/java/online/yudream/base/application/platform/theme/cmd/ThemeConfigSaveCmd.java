package online.yudream.base.application.platform.theme.cmd;

import lombok.Data;

import java.util.Map;

/** 主题配置保存命令：values 为扁平键值，键集合由主题 schema 约束。 */
@Data
public class ThemeConfigSaveCmd {

    private String themeCode;
    private Map<String, Object> values;
}
