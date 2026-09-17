package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PluginMarketplaceBatchInstallRequest {

    @Valid
    @NotEmpty(message = "安装清单不能为空")
    private List<Item> items;

    @Data
    public static class Item {

        @NotBlank(message = "插件编码不能为空")
        private String code;

        @NotBlank(message = "插件版本不能为空")
        private String releaseVersion;

        /** 可选：指定从哪个市场源安装；缺省按安装来源与源优先级解析。 */
        private String sourceCode;
    }
}
