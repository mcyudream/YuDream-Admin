package online.yudream.base.interfaces.platform.ai.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 模型发现结果：listed=false 表示该端点无模型列表接口，需手工登记。 */
@Data
@Builder
public class AiModelDiscoveryRes {
    private List<AiDiscoveredModelRes> models;
    private Boolean listed;
    private String message;
}
