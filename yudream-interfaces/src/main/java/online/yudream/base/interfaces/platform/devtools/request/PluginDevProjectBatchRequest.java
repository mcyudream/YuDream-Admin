package online.yudream.base.interfaces.platform.devtools.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 从父目录批量登记开发模式项目请求。
 */
@Data
public class PluginDevProjectBatchRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父目录（宿主机绝对路径），会有界扫描其中的插件模块子目录 */
    @NotBlank(message = "插件目录不能为空")
    private String path;
}
