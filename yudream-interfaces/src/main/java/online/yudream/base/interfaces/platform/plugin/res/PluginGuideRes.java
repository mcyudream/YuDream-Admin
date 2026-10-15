package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginGuideRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String code;
    private String title;
    private String routePath;
    private String permission;
    private Integer sort;
    private List<PluginGuideStepRes> steps;
}
