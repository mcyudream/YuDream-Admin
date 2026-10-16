package online.yudream.base.application.platform.wiki.service;

/**
 * Wiki 稳定权限码（全局能力闸门）。空间实例 ACL 使用独立的成员数据，
 * 禁止把 spaceId 拼进权限码；新增端点必须经 {@code @PermissionRegister} 注册。
 */
public final class WikiAccessPermissions {
    /** 查看知识库列表/目录。 */
    public static final String SPACE_VIEW = "platform:wiki:view";
    /** 创建/编辑知识库设置。 */
    public static final String SPACE_MANAGE = "platform:wiki:manage";
    /** 删除知识库。 */
    public static final String SPACE_DELETE = "platform:wiki:delete";
    /** 编辑目录/页面。 */
    public static final String NODE_EDIT = "platform:wiki:edit";
    /** 发布/取消发布页面。 */
    public static final String PAGE_PUBLISH = "platform:wiki:publish";
    /** 更新/随机空间封面。 */
    public static final String COVER_EDIT = "platform:wiki:cover:edit";
    /** 移除空间封面。 */
    public static final String COVER_DELETE = "platform:wiki:cover:delete";
    /** 查看空间成员。 */
    public static final String MEMBER_VIEW = "platform:wiki:member:view";
    /** 新增/调整成员授权、启用受限 ACL。 */
    public static final String MEMBER_MANAGE = "platform:wiki:member:manage";
    /** 移除空间成员。 */
    public static final String MEMBER_DELETE = "platform:wiki:member:delete";
    /** 查看评论治理列表。 */
    public static final String COMMENT_VIEW = "platform:wiki:comment:view";
    /** 审核评论（发布/隐藏/恢复/置顶）。 */
    public static final String COMMENT_MANAGE = "platform:wiki:comment:manage";
    /** 删除评论（软删除，可恢复）。 */
    public static final String COMMENT_DELETE = "platform:wiki:comment:delete";

    private WikiAccessPermissions() {
    }
}
