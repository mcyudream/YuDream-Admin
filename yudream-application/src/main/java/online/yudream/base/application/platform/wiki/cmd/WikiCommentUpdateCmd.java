package online.yudream.base.application.platform.wiki.cmd;

import lombok.Data;

import java.io.Serializable;

/**
 * 公开端编辑本人评论输入。
 */
@Data
public class WikiCommentUpdateCmd implements Serializable {
    private String spaceSlug;
    private Long commentId;
    private String content;
}
