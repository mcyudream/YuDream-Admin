package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PluginMarketPublicationReviewRequest {

    @Size(max = 512, message = "审核备注过长")
    private String note;
}
