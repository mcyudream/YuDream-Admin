package online.yudream.base.application.common.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 搜索字段配置
 * <p>
 * 从 {@link Searchable} 注解中提取的字段元数据,用于前端动态生成筛选表单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchFieldConfig implements Serializable {

    /**
     * 字段名称(Java字段名)
     */
    private String fieldName;

    /**
     * 显示标签
     */
    private String label;

    /**
     * 输入框占位符
     */
    private String placeholder;

    /**
     * 控件类型
     */
    private ControlType controlType;

    /**
     * 显示顺序
     */
    private Integer order;

    /**
     * 是否在折叠时显示
     */
    private Boolean showWhenFolded;

    /**
     * 控件宽度
     */
    private String width;

    /**
     * 分组名称
     */
    private String group;

    /**
     * 选项数据源标识
     */
    private String optionsSource;

    /**
     * 提示信息
     */
    private String hint;

    /**
     * 字段类型(用于前端类型推断)
     */
    private String fieldType;
}
