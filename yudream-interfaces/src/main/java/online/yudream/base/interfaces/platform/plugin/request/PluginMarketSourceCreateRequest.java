package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PluginMarketSourceCreateRequest {

    @NotBlank(message = "市场源标识不能为空")
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,31}", message = "市场源标识只能是 32 位以内的小写字母、数字或连字符")
    private String code;

    @NotBlank(message = "市场源名称不能为空")
    @Size(max = 128, message = "市场源名称过长")
    private String name;

    @NotBlank(message = "市场源地址不能为空")
    @Size(max = 2048, message = "市场源地址过长")
    private String rootUrl;

    private String token;

    private Integer sortOrder;
}
