package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 公开端编辑本人评论请求。
 */
@Data
public class WikiCommentUpdateRequest {
    @NotBlank(message = "评论内容不能为空")
    private String content;
}
