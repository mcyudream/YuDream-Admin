package online.yudream.base.interfaces.installer.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MongoDB 探测请求。
 */
@Data
public class MongoProbeRequest {

    @NotBlank(message = "MongoDB 连接串不能为空")
    private String uri;
}
