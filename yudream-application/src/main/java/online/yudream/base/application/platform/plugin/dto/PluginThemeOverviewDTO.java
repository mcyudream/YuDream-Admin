package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 插件主题总览：全部已启用主题与每个 scope 当前激活的插件编码（无激活则为空）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginThemeOverviewDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<PluginThemeDTO> themes;
    /** scope -> 激活主题的插件编码。 */
    private Map<String, String> active;
}
