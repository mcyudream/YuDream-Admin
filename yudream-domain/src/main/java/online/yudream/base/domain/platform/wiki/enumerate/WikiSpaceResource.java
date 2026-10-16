package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * {@code @WikiSpaceAccess} 注解解析空间上下文的依据。
 */
public enum WikiSpaceResource {
    /** 方法参数直接携带 spaceId（默认参数名 spaceId）。 */
    SPACE,
    /** 方法参数或请求体携带 spaceSlug，需要按 slug 反查空间。 */
    SPACE_SLUG,
    /** 方法参数携带资料 ID，需要按资料反查所属空间。 */
    SOURCE,
    /** 方法参数携带摄入任务 ID。 */
    INGEST_TASK,
    /** 方法参数携带审阅项 ID。 */
    REVIEW,
    /** 方法参数携带节点 ID。 */
    NODE
}
