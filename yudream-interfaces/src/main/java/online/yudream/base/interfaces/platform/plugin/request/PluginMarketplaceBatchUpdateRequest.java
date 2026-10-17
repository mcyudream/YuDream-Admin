package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 批量更新市场插件请求：items 只应包含更新计划中未受阻的插件。 */
@Data
public class PluginMarketplaceBatchUpdateRequest {

    @Valid
    @NotEmpty(message = "更新清单不能为空")
    private List<Item> items;

    @Data
    public static class Item {

        @NotBlank(message = "插件编码不能为空")
        private String code;

        @NotBlank(message = "目标版本不能为空")
        private String releaseVersion;

        /** 可选：指定从哪个市场源更新；缺省按安装来源与源优先级解析。 */
        private String sourceCode;
    }
}
