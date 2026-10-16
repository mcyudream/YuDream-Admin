package online.yudream.base.application.platform.wiki.cmd;

import lombok.Data;

import java.io.Serializable;

/**
 * 公开端评论文本锚点输入（客户端捕获草稿，字段与公开端捕获组件同构）。
 * <p>
 * 只接受引用文本与定位提示；版本号/内容哈希/空间与作者身份一律由服务端从当前
 * 已发布版本与认证上下文推导，请求携带的对应字段不会被采纳。
 */
@Data
public class WikiCommentAnchorInput implements Serializable {
    private String exact;
    private String prefix;
    private String suffix;
    /** 捕获时所在块序号（提示）。 */
    private Integer blockOrdinal;
    /** 捕获时所在块类型（提示，lowerCamel 线协议名）。 */
    private String blockType;
    /** 表格行序提示（仅表格单元格）。 */
    private Integer tableRow;
    /** 表格列序提示（仅表格单元格）。 */
    private Integer tableCell;
    /** 捕获时起始偏移提示（归一化文本坐标）。 */
    private Integer startOffset;
    /** 捕获时结束偏移提示（归一化文本坐标，不含）。 */
    private Integer endOffset;
}
