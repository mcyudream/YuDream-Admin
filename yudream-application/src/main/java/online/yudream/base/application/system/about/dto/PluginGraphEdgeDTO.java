package online.yudream.base.application.system.about.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件依赖图边：方向为 依赖方(consumer) -> 被依赖方(provider)，kind 区分硬/软依赖。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginGraphEdgeDTO {

    private String source;
    private String target;
    /** HARD（depend）/ SOFT（softdepend） */
    private String kind;
}
