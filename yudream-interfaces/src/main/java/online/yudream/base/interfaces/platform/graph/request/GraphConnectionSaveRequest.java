package online.yudream.base.interfaces.platform.graph.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import java.util.Set;

/** Logical graph-table request. */
@Data
public class GraphConnectionSaveRequest {
    @NotBlank(message = "逻辑图表名称不能为空") private String name;
    @NotBlank(message = "逻辑图表编码不能为空") private String code;
    private String description;
    private GraphConnectionStatus status;
    private Set<String> authorizedPluginCodes;
}
