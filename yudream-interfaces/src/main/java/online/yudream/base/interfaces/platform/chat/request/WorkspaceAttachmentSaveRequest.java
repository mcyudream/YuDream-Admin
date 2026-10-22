package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceAttachmentSaveRequest {
    @NotBlank(message = "附件不能为空")
    private String fileId;
    /** 目标目录，空为根目录 */
    private String directory;
}
