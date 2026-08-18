package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatAttachmentRequest {
    @NotBlank(message = "附件 ID 不能为空")
    private String fileId;
    private String fileName;
    private String contentType;
    private Long size;
    private String kind;
    private String url;
    private String extractedText;
    private String dataUrl;
}
