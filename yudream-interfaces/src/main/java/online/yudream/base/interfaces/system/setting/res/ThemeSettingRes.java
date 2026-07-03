package online.yudream.base.interfaces.system.setting.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeSettingRes {

    private Map<String, Object> config;
}
