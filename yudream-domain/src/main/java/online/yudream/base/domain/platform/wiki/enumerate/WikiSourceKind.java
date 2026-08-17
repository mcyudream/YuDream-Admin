package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 原始资料来源类型。
 */
public enum WikiSourceKind {
    /** 上传的本地文件（PDF、Office、电子书等）。 */
    FILE,
    /** 通过 URL 导入的网页/文件。 */
    URL,
    /** 在线编辑的 Markdown 文本资料。 */
    TEXT
}
