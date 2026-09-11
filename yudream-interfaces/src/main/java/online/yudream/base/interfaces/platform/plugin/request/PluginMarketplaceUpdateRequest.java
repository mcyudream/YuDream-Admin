package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PluginMarketplaceUpdateRequest {

    @NotBlank(message = "插件版本不能为空")
    private String releaseVersion;

    /** 可选：指定从哪个市场源更新；缺省按安装来源与源优先级解析。 */
    private String sourceCode;
}
