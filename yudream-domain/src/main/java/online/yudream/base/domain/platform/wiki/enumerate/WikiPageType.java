package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * Wiki 页面类型，对应 llm_wiki 的 wiki 目录分类。
 * <p>
 * 页面类型决定：默认目录、图谱类型亲和信号、以及摄入时 LLM 的组织方式。
 */
public enum WikiPageType {
    SOURCE_SUMMARY("资料摘要"),
    SOURCE_DOCUMENT("原文档"),
    ENTITY("实体"),
    CONCEPT("概念"),
    SYNTHESIS("综合分析"),
    COMPARISON("对比"),
    QUERY("查询归档"),
    RESEARCH("深度研究"),
    OVERVIEW("全局概要"),
    INDEX("内容目录"),
    LOG("操作日志");

    private final String label;

    WikiPageType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
