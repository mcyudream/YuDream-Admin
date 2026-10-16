package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 知识库实例 ACL 模式。
 * <p>
 * 存量空间缺省 {@link #LEGACY}，保持既有「全局权限码」行为；新建的受限空间为
 * {@link #RESTRICTED}，在全局权限码之外还必须命中空间成员授权，缺失一律按拒绝处理。
 */
public enum WikiSpaceAclMode {
    /** 兼容模式：不启用空间成员 ACL，只依赖全局权限码。 */
    LEGACY,
    /** 受限模式：全局权限码 + 空间成员 ACL 双重判定，fail closed。 */
    RESTRICTED;

    /** 空值一律回落 LEGACY（旧数据没有该字段，不能因此改变行为，更不能因此锁死）。 */
    public static WikiSpaceAclMode of(WikiSpaceAclMode mode) {
        return mode == null ? LEGACY : mode;
    }
}
