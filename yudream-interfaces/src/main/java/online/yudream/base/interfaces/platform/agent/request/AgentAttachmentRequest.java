package online.yudream.base.interfaces.platform.agent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentAttachmentRequest {
    @NotBlank
    @Size(max = 255)
    private String name;
    @NotBlank
    @Size(max = 160)
    private String contentType;
    @PositiveOrZero
    private Long size;
    @NotBlank
    @Size(max = 14_000_000)
    private String dataUrl;
}
