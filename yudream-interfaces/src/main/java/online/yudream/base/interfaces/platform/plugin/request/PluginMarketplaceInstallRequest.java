package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PluginMarketplaceInstallRequest {

    @NotBlank(message = "插件版本不能为空")
    private String releaseVersion;
}
