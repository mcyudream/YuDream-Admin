package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PluginMarketSourceTestRequest {

    @NotBlank(message = "市场源地址不能为空")
    @Size(max = 2048, message = "市场源地址过长")
    private String rootUrl;

    private String token;
}
