package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginThemeOverviewRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<PluginThemeRes> themes;
    private Map<String, String> active;
}
