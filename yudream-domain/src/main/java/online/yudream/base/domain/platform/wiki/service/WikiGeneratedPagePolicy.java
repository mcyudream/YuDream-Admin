package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;

/**
 * 生成内容覆盖策略：LLM 摄入不得静默覆盖人工编辑过的页面。
 * <p>
 * 只有同时满足以下条件的草稿才允许被后续生成链路重新写入：
 * <ul>
 *   <li>草稿带 {@link WikiFrontmatter#ORIGIN_LLM} 生成来源标记；</li>
 *   <li>页面尚未发布，或当前草稿与最后一次发布版本完全一致（说明生成后又发布了、期间没有人再改草稿）。</li>
 * </ul>
 * 其余情况（人工创建、旧数据无标记、发布后又被人工修改）一律视为人工内容，调用方必须保留现有草稿，
 * 改为记录审核提案而不是直接覆盖。
 */
public final class WikiGeneratedPagePolicy {

    private WikiGeneratedPagePolicy() {
    }

    /**
     * 判断生成链路是否允许覆盖当前草稿。
     *
     * @param draftMarkdown     当前草稿全文（可为 null，表示节点没有内容）
     * @param publishedMarkdown 当前发布版本全文；null 表示页面尚未发布
     */
    public static boolean mayOverwriteDraft(String draftMarkdown, String publishedMarkdown) {
        if (draftMarkdown == null) {
            return true;
        }
        WikiFrontmatter frontmatter = WikiFrontmatter.parse(draftMarkdown);
        if (!frontmatter.llmGenerated()) {
            return false;
        }
        return publishedMarkdown == null || draftMarkdown.equals(publishedMarkdown);
    }
}
