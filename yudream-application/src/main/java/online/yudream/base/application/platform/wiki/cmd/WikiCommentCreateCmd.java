package online.yudream.base.application.platform.wiki.cmd;

import lombok.Data;

import java.io.Serializable;

/**
 * 公开端发表评论输入。作者身份只从认证上下文推导，请求不携带作者/空间归属字段；
 * 锚点只接受引用文本与定位提示，版本信息由服务端按当前已发布版本盖章。
 */
@Data
public class WikiCommentCreateCmd implements Serializable {
    private String spaceSlug;
    private Long nodeId;
    /** 父评论 ID；为空表示根评论，仅支持两级回复。 */
    private Long parentId;
    private String content;
    /** 文本锚点；为空表示不选文字的传统评论。回复不允许携带锚点（继承根评论）。 */
    private WikiCommentAnchorInput anchor;
}
