package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 摄入时 LLM 标记、等待人工处理的审阅项类型。
 */
public enum WikiReviewItemType {
    CREATE_PAGE,
    DEEP_RESEARCH,
    SKIP,
    FLAG
}
