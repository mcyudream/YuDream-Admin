package online.yudream.base.interfaces.platform.render.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.render.model.RenderModels.SourceType;

import java.util.Map;

@Data
public class MessageRenderRequest {
    private SourceType sourceType;
    @NotBlank
    private String content;
    private Integer width;
    private Integer maxHeight;
    private Boolean transparent;
    private String format;
    private Map<String, Object> options;
}
