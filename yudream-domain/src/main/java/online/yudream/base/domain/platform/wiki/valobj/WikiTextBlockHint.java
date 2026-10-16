package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiTextBlockType;

/**
 * 文本锚点的块提示值对象（客户端定位参考，不参与服务端唯一性判定）。
 * <p>
 * 只做结构性校验：序号非负；{@link WikiTextBlockType#TABLE_CELL} 必须同时给出非负行列，
 * 非表格类型不得携带行列。类型缺失或未知一律拒绝，不落库含糊的定位提示。
 *
 * @param type       块类型
 * @param ordinal    块序号，从 0 开始
 * @param tableRow   表格行号（仅 TABLE_CELL）
 * @param tableCell  表格列号（仅 TABLE_CELL）
 */
public record WikiTextBlockHint(WikiTextBlockType type, int ordinal, Integer tableRow, Integer tableCell) {

    public WikiTextBlockHint {
        if (type == null) {
            throw new BizException("引用块类型不能为空");
        }
        if (ordinal < 0) {
            throw new BizException("引用块序号不合法");
        }
        if (type == WikiTextBlockType.TABLE_CELL) {
            if (tableRow == null || tableCell == null) {
                throw new BizException("表格锚点必须提供行列信息");
            }
            if (tableRow < 0 || tableCell < 0) {
                throw new BizException("表格锚点行列不合法");
            }
        }
        else if (tableRow != null || tableCell != null) {
            throw new BizException("非表格锚点不能携带行列信息");
        }
    }

    /** 非表格块提示。 */
    public static WikiTextBlockHint of(WikiTextBlockType type, int ordinal) {
        return new WikiTextBlockHint(type, ordinal, null, null);
    }

    /** 表格单元格块提示。 */
    public static WikiTextBlockHint tableCell(int ordinal, int row, int cell) {
        return new WikiTextBlockHint(WikiTextBlockType.TABLE_CELL, ordinal, row, cell);
    }

    /** 解析客户端块提示；类型未知/缺失或字段结构不合法一律拒绝。 */
    public static WikiTextBlockHint of(String type, Integer ordinal, Integer tableRow, Integer tableCell) {
        WikiTextBlockType blockType = WikiTextBlockType.of(type);
        if (blockType == null) {
            throw new BizException("引用块类型不合法");
        }
        if (ordinal == null) {
            throw new BizException("引用块序号不能为空");
        }
        if (blockType == WikiTextBlockType.TABLE_CELL) {
            if (tableRow == null || tableCell == null) {
                throw new BizException("表格锚点必须提供行列信息");
            }
            return tableCell(ordinal, tableRow, tableCell);
        }
        if (tableRow != null || tableCell != null) {
            throw new BizException("非表格锚点不能携带行列信息");
        }
        return of(blockType, ordinal);
    }
}
