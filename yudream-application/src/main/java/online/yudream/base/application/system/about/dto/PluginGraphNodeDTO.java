package online.yudream.base.application.system.about.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件依赖图节点：状态用字符串承载（ENABLED/LOADED/INSTALLED/DISABLED/ERROR），前端据此着色。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginGraphNodeDTO {

    private String code;
    private String name;
    private String version;
    private String status;
    private String errorMessage;
}
