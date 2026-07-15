package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WikiSearchRequest {
    private String spaceSlug;
    @NotBlank(message = "检索关键词不能为空")
    private String query;
    private int topK = 8;
    private String pathPrefix;
    private boolean graphExpansion;
}
