package online.yudream.base.application.platform.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模型发现结果；{@code listed=false} 表示该端点没有模型列表接口，管理端应回落到手工登记。
 */
@Data
@Builder
public class AiModelDiscoveryDTO {
    private List<AiDiscoveredModelDTO> models;
    private Boolean listed;
    private String message;
}
