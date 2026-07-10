package online.yudream.base.application.platform.render.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.render.model.RenderModels.SourceType;

import java.util.Map;

@Data
public class MessageRenderCmd {
    private SourceType sourceType;
    private String content;
    private Integer width;
    private Integer maxHeight;
    private Boolean transparent;
    private String format;
    private Map<String, Object> options;
}
