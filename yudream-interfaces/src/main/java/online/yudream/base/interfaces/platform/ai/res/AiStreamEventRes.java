package online.yudream.base.interfaces.platform.ai.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiStreamEventRes {
    private String content;
    private CmsPageGenerateRes result;
    private AiToolCallRes tool;
}
