package online.yudream.base.domain.platform.wiki.enumerate;

import java.util.Locale;

/**
 * 文本锚点引用的块类型（客户端定位提示，不是落库判定的依据）。
 * <p>
 * 线协议使用 lowerCamel 名称（与公开端捕获组件一致）：paragraph/heading/listItem/blockquote/
 * codeBlock/tableCell；服务端解析时同时兼容枚举名与 SNAKE_CASE，未知类型返回 null，
 * 由调用方决定拒绝（写入）或丢弃提示（读取）。
 */
public enum WikiTextBlockType {
    /** 普通段落。 */
    PARAGRAPH("paragraph"),
    /** 标题行。 */
    HEADING("heading"),
    /** 列表项。 */
    LIST_ITEM("listItem"),
    /** 引用块。 */
    QUOTE("blockquote"),
    /** 代码块。 */
    CODE_BLOCK("codeBlock"),
    /** 表格单元格（必须携带行列提示）。 */
    TABLE_CELL("tableCell");

    private final String wireValue;

    WikiTextBlockType(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 线协议名称（lowerCamel）。 */
    public String wireValue() {
        return wireValue;
    }

    /** 解析线协议名/枚举名/SNAKE_CASE；未知或空一律返回 null。 */
    public static WikiTextBlockType of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (WikiTextBlockType type : values()) {
            if (type.wireValue.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        try {
            return valueOf(normalized.replace('-', '_').toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
