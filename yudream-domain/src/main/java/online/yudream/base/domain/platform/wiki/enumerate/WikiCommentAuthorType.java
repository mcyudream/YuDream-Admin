package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 评论作者归属类型。登录评论的作者 ID 只能由认证上下文推导；
 * 匿名评论需要空间显式开启并配套限流风控，默认不开放。
 */
public enum WikiCommentAuthorType {
    USER,
    ANONYMOUS
}
