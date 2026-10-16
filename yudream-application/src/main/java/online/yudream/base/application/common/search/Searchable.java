package online.yudream.base.application.common.search;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记查询类字段为可搜索字段,前端将根据此注解自动生成筛选表单控件
 * <p>
 * 使用示例:
 * <pre>
 * {@code @Searchable(label = "用户名", placeholder = "请输入用户名", order = 1)}
 * private String username;
 *
 * {@code @Searchable(label = "状态", controlType = ControlType.SELECT, order = 2)}
 * private UserStatus status;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Searchable {

    /**
     * 显示标签文本
     * <p>
     * 如果为空,将尝试根据字段名自动生成(驼峰转空格)
     */
    String label() default "";

    /**
     * 输入框占位符文本
     */
    String placeholder() default "";

    /**
     * 控件类型
     * <p>
     * 默认为AUTO,将根据字段类型自动推断:
     * <ul>
     *   <li>String -> TEXT</li>
     *   <li>Enum -> SELECT</li>
     *   <li>LocalDate/Date -> DATE_RANGE</li>
     *   <li>Number -> NUMBER_RANGE</li>
     *   <li>Boolean -> SELECT(是/否)</li>
     * </ul>
     */
    ControlType controlType() default ControlType.AUTO;

    /**
     * 在筛选表单中的显示顺序
     * <p>
     * 数值越小越靠前,默认为0
     */
    int order() default 0;

    /**
     * 是否在折叠状态下仍然显示
     * <p>
     * 设为true的字段会在搜索区折叠时仍然可见,通常用于最常用的关键词搜索字段
     */
    boolean showWhenFolded() default false;

    /**
     * 控件宽度(CSS值)
     * <p>
     * 例如: "200px", "auto", "100%"
     */
    String width() default "200px";

    /**
     * 分组名称
     * <p>
     * 用于将相关字段归到一起,相同分组的字段在UI上可能会被视觉分组
     */
    String group() default "";

    /**
     * 选项数据源标识
     * <p>
     * 对于SELECT/CHECKBOX/RADIO类型,指定选项数据的来源标识
     * 前端将根据此标识调用相应的API获取选项列表
     * <p>
     * 例如: "deptOptions", "roleOptions", "statusOptions"
     */
    String optionsSource() default "";

    /**
     * 提示信息
     * <p>
     * 在控件旁边显示的帮助文本或提示信息
     */
    String hint() default "";
}
