package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PluginMarketReviewRequiredRequest {

    @NotNull(message = "reviewRequired 不能为空")
    private Boolean reviewRequired;
}
