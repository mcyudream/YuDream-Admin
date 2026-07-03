package online.yudream.base.interfaces.system.setting.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ThemeSettingUpdateRequest {

    @NotNull(message = "主题配置不能为空")
    private Map<String, Object> config;
}
