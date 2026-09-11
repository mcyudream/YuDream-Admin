package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PluginMarketSourceUpdateRequest {

    @NotBlank(message = "市场源标识不能为空")
    private String id;

    @NotBlank(message = "市场源名称不能为空")
    @Size(max = 128, message = "市场源名称过长")
    private String name;

    @Size(max = 2048, message = "市场源地址过长")
    private String rootUrl;

    /** 空白表示保留现有令牌。 */
    private String token;

    private Integer sortOrder;
}
