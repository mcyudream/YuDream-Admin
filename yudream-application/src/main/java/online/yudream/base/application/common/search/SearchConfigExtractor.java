package online.yudream.base.application.common.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 搜索配置提取器
 * <p>
 * 从查询类中提取 {@link Searchable} 注解标记的字段,生成前端可用的搜索配置
 */
@Slf4j
public class SearchConfigExtractor {

    /**
     * 从查询类中提取搜索字段配置
     *
     * @param queryClass 查询类(通常继承自PageQuery)
     * @return 搜索配置对象
     */
    public static SearchConfig extractFrom(Class<?> queryClass) {
        List<SearchFieldConfig> fields = new ArrayList<>();

        // 遍历所有字段(包括父类字段)
        for (Field field : getAllFields(queryClass)) {
            Searchable ann = field.getAnnotation(Searchable.class);
            if (ann == null) {
                continue;
            }

            SearchFieldConfig config = SearchFieldConfig.builder()
                    .fieldName(field.getName())
                    .label(resolveLabel(ann, field))
                    .placeholder(ann.placeholder())
                    .controlType(resolveControlType(ann, field))
                    .order(ann.order())
                    .showWhenFolded(ann.showWhenFolded())
                    .width(ann.width())
                    .group(ann.group())
                    .optionsSource(ann.optionsSource())
                    .hint(ann.hint())
                    .fieldType(field.getType().getSimpleName())
                    .build();

            fields.add(config);
        }

        // 按order排序
        fields.sort(Comparator.comparingInt(SearchFieldConfig::getOrder));

        SearchConfig searchConfig = new SearchConfig(fields);
        searchConfig.setQueryClassName(queryClass.getName());
        searchConfig.setQuerySimpleName(queryClass.getSimpleName());

        return searchConfig;
    }

    /**
     * 获取类及其父类的所有字段
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;

        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                fields.add(field);
            }
            currentClass = currentClass.getSuperclass();
        }

        return fields;
    }

    /**
     * 解析控件类型
     * <p>
     * 如果注解中指定了非AUTO类型,直接使用;
     * 否则根据字段类型自动推断
     */
    private static ControlType resolveControlType(Searchable ann, Field field) {
        if (ann.controlType() != ControlType.AUTO) {
            return ann.controlType();
        }

        // 根据字段类型自动推断
        Class<?> type = field.getType();

        // 字符串 -> 文本框
        if (type == String.class) {
            return ControlType.TEXT;
        }

        // 枚举 -> 下拉选择
        if (type.isEnum()) {
            return ControlType.SELECT;
        }

        // 日期类型 -> 日期范围
        if (type == LocalDate.class || type == LocalDateTime.class || type == Date.class) {
            return ControlType.DATE_RANGE;
        }

        // Boolean -> 下拉选择(是/否)
        if (type == Boolean.class || type == boolean.class) {
            return ControlType.SELECT;
        }

        // 数值类型 -> 数值范围
        if (Number.class.isAssignableFrom(type) || type.isPrimitive() && !type.equals(boolean.class)) {
            // 如果有optionsSource,说明是外键ID,应该用下拉选择
            if (StringUtils.hasText(ann.optionsSource())) {
                return ControlType.SELECT;
            }
            return ControlType.NUMBER_RANGE;
        }

        // 默认文本框
        return ControlType.TEXT;
    }

    /**
     * 解析显示标签
     * <p>
     * 如果注解中指定了标签,直接使用;
     * 否则将驼峰命名转换为可读文本(简单实现)
     */
    private static String resolveLabel(Searchable ann, Field field) {
        if (StringUtils.hasText(ann.label())) {
            return ann.label();
        }

        // 驼峰转空格的简单实现
        // 例如: userName -> user Name
        String fieldName = field.getName();
        StringBuilder label = new StringBuilder();

        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                label.append(' ');
            }
            label.append(c);
        }

        return label.toString();
    }
}
