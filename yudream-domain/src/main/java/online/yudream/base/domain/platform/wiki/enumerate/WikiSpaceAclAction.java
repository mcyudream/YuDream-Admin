package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 空间成员可授予的动作。授权的语义包含关系（低阶动作被高阶动作覆盖）：
 * {@code MANAGE > EDIT > COMMENT > READ}，避免授予 MANAGE 的管理员反而无法编辑。
 */
public enum WikiSpaceAclAction {
    /** 读取空间（目录、页面、治理数据）。 */
    READ,
    /** 在空间页面发表/维护本人评论。 */
    COMMENT,
    /** 编辑空间内容（目录、页面、资料、摄入、发布）。 */
    EDIT,
    /** 管理空间（成员、封面、设置、审核）。 */
    MANAGE;

    /** 本动作是否覆盖目标动作（含自身）。 */
    public boolean implies(WikiSpaceAclAction target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case READ -> target == READ;
            case COMMENT -> target == READ || target == COMMENT;
            case EDIT -> target == READ || target == COMMENT || target == EDIT;
            case MANAGE -> true;
        };
    }
}
