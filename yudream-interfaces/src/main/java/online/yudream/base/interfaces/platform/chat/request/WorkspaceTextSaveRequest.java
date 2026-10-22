package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkspaceTextSaveRequest {
    @NotBlank(message = "路径不能为空")
    private String path;
    @NotNull(message = "内容不能为空")
    private String content;
    /** 打开时的修订号；新建文件传 0，不传则不做并发校验 */
    private Integer expectedRevision;
}
