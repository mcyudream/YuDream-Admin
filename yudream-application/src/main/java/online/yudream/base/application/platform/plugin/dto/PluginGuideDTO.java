package online.yudream.base.application.platform.plugin.dto;

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
public class PluginGuideDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String code;
    private String title;
    private String routePath;
    private String permission;
    private int sort;
    private List<PluginGuideStepDTO> steps;
}
