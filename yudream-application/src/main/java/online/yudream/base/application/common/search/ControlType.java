package online.yudream.base.application.common.search;

/**
 * 搜索表单控件类型
 */
public enum ControlType {
    /**
     * 自动推断:根据字段类型自动选择最合适的控件
     */
    AUTO,

    /**
     * 文本输入框
     */
    TEXT,

    /**
     * 下拉选择框
     */
    SELECT,

    /**
     * 日期范围选择器
     */
    DATE_RANGE,

    /**
     * 数值范围输入(最小值-最大值)
     */
    NUMBER_RANGE,

    /**
     * 复选框组
     */
    CHECKBOX,

    /**
     * 单选按钮组
     */
    RADIO
}
