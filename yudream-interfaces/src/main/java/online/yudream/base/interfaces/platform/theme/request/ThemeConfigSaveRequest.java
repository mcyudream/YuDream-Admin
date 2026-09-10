package online.yudream.base.interfaces.platform.theme.request;

import lombok.Data;

import java.util.Map;

/** 主题配置保存请求：values 为扁平键值，键集合与类型由主题 schema 约束。 */
@Data
public class ThemeConfigSaveRequest {

    private Map<String, Object> values;
}
