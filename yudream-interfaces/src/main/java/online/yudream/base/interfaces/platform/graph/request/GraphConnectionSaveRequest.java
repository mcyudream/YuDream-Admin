package online.yudream.base.interfaces.platform.graph.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;

@Data
public class GraphConnectionSaveRequest {
    @NotBlank(message = "连接名称不能为空")
    private String name;
    @NotBlank(message = "连接编码不能为空")
    private String code;
    @NotBlank(message = "连接地址不能为空")
    private String uri;
    @NotBlank(message = "用户名不能为空")
    private String username;
    private String password;
    private String database;
    private GraphConnectionStatus status;
}
