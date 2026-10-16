package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 公开端发表评论请求。作者身份由服务端从认证上下文推导，请求不携带作者字段；
 * 文本锚点只提交引用文本与定位提示，版本信息由服务端盖章。
 */
@Data
public class WikiCommentCreateRequest {
    /** 父评论 ID（字符串长 ID）；为空表示根评论。 */
    private String parentId;
    @NotBlank(message = "评论内容不能为空")
    private String content;
    /** 文本锚点；为空表示不选文字的评论。回复不允许携带锚点（继承根评论）。 */
    @Valid
    private WikiCommentAnchorRequest anchor;
}
