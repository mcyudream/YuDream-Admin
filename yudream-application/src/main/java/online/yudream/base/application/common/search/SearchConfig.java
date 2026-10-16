package online.yudream.base.application.common.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 搜索配置
 * <p>
 * 包含某个查询类的所有可搜索字段配置
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchConfig implements Serializable {

    /**
     * 字段配置列表(已按order排序)
     */
    private List<SearchFieldConfig> fields;

    /**
     * 查询类的全限定名
     */
    private String queryClassName;

    /**
     * 查询类的简单名称
     */
    private String querySimpleName;

    public SearchConfig(List<SearchFieldConfig> fields) {
        this.fields = fields;
    }
}
