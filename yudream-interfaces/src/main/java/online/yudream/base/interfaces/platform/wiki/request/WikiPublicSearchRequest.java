package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WikiPublicSearchRequest {
    @NotBlank(message = "检索关键词不能为空")
    @Size(max = 200, message = "检索关键词不能超过 200 个字符")
    private String query;

    @Size(max = 100, message = "知识库标识不能超过 100 个字符")
    private String spaceSlug;
}
