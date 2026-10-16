package online.yudream.base.interfaces.platform.wiki.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公开端评论文本锚点请求（客户端捕获草稿，字段与公开端捕获组件同构）。
 * <p>
 * 只接受引用文本与定位提示：版本号、修订号、内容哈希、空间与作者身份一律不接受，
 * 由服务端按当前已发布版本与认证上下文推导。
 * blockOrdinal/blockType 必须成对出现（要么都不给）；tableRow/tableCell 只随 tableCell
 * 类型出现（服务端与领域仍会二次校验，此处只做接口层快速失败）；
 * startOffset/endOffset 为归一化文本坐标的可选提示，两者都给时必须 startOffset &lt; endOffset。
 */
@Data
public class WikiCommentAnchorRequest {
    /** 引用文本；服务端按空白折叠归一化后要求 1..512 字符且在已发布正文中唯一定位。 */
    @NotBlank(message = "引用文本不能为空")
    @Size(max = 512, message = "引用文本不能超过 512 个字符")
    private String exact;
    /** 引用前的上下文（归一化后最多 64 字符），用于多处命中时消歧。 */
    @Size(max = 64, message = "引用前缀不能超过 64 个字符")
    private String prefix;
    /** 引用后的上下文（归一化后最多 64 字符），用于多处命中时消歧。 */
    @Size(max = 64, message = "引用后缀不能超过 64 个字符")
    private String suffix;
    /** 捕获时所在块序号（提示）。 */
    @Min(value = 0, message = "引用块序号不合法")
    private Integer blockOrdinal;
    /** 捕获时所在块类型（提示）：paragraph/heading/listItem/blockquote/codeBlock/tableCell。 */
    @Size(max = 32, message = "引用块类型不合法")
    private String blockType;
    /** 表格行序提示（仅 tableCell）。 */
    @Min(value = 0, message = "表格行号不合法")
    private Integer tableRow;
    /** 表格列序提示（仅 tableCell）。 */
    @Min(value = 0, message = "表格列号不合法")
    private Integer tableCell;
    /** 捕获时起始偏移提示（归一化文本坐标）。 */
    @Min(value = 0, message = "锚点起始偏移不合法")
    private Integer startOffset;
    /** 捕获时结束偏移提示（归一化文本坐标，不含）。 */
    @Min(value = 0, message = "锚点结束偏移不合法")
    private Integer endOffset;

    /** 块提示成对：blockType 与 blockOrdinal 要么都给、要么都不给。 */
    @AssertTrue(message = "引用块提示必须同时提供 blockType 与 blockOrdinal")
    @JsonIgnore
    public boolean isBlockHintPaired() {
        boolean hasType = blockType != null && !blockType.isBlank();
        boolean hasOrdinal = blockOrdinal != null;
        return hasType == hasOrdinal;
    }

    /** 表格行列只随引用块提示提供（类型是否为 tableCell 由服务端按别名解析后判定）。 */
    @AssertTrue(message = "表格行列提示必须随引用块提示一起提供")
    @JsonIgnore
    public boolean isTableHintScoped() {
        boolean hasTableHint = tableRow != null || tableCell != null;
        boolean hasBlockHint = blockOrdinal != null && blockType != null && !blockType.isBlank();
        return !hasTableHint || hasBlockHint;
    }
}
