package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ChatQuotaConfigRequest {
    @Min(value = 1, message = "每日 token 上限必须大于 0")
    private long dailyTokenLimit;
}
