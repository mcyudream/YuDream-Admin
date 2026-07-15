package online.yudream.base.domain.platform.cms.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CmsBlock extends BaseDomain {

    private String code;
    private String name;
    private String description;
    private String category;
    private CmsBlockKind kind;
    private String icon;
    private String previewImageUrl;
    private String htmlContent;
    private String cssContent;
    private String jsContent;
    private String builderProjectJson;
    private List<String> tags;
    private Boolean enabled;
    private Boolean builtin;
    private Integer sort;

    public static CmsBlock create(String code, String name, CmsBlockKind kind) {
        CmsBlock block = new CmsBlock();
        block.code = required(code, "区块编码不能为空");
        block.name = required(name, "区块名称不能为空");
        block.kind = kind == null ? CmsBlockKind.ATOMIC : kind;
        block.tags = new ArrayList<>();
        block.enabled = true;
        block.builtin = false;
        block.sort = 0;
        return block;
    }

    public void update(String name, String description, String category, String icon,
                       String previewImageUrl, String htmlContent, String cssContent,
                       String jsContent, String builderProjectJson, List<String> tags,
                       Boolean enabled, Integer sort) {
        this.name = name == null || name.trim().isEmpty() ? this.name : name.trim();
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.previewImageUrl = previewImageUrl;
        this.htmlContent = htmlContent;
        this.cssContent = cssContent;
        this.jsContent = jsContent;
        this.builderProjectJson = builderProjectJson;
        this.tags = normalizeTerms(tags);
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (sort != null) {
            this.sort = sort;
        }
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private static List<String> normalizeTerms(List<String> terms) {
        if (terms == null) {
            return new ArrayList<>();
        }
        return terms.stream()
                .filter(term -> term != null && !term.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();
    }
}
