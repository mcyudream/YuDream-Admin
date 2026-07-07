package online.yudream.base.domain.platform.cms.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;
import online.yudream.base.domain.platform.cms.valobj.PageSlug;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CmsPage extends BaseDomain {

    private String title;
    private String slug;
    private String summary;
    private String excerpt;
    private String coverImageUrl;
    private List<String> categories;
    private List<String> tags;
    private String markdownContent;
    private String htmlContent;
    private String cssContent;
    private String jsContent;
    private String builderProjectJson;
    private String seoTitle;
    private String seoDescription;
    private PageTemplate template;
    private PageStatus status;
    private LocalDateTime publishedAt;

    public static CmsPage create(String title, String slug) {
        CmsPage page = new CmsPage();
        page.title = required(title, "页面标题不能为空");
        page.slug = PageSlug.of(slug).value();
        page.template = PageTemplate.DEFAULT;
        page.status = PageStatus.DRAFT;
        return page;
    }

    public void update(String title, String slug, String summary, String excerpt, String coverImageUrl,
                       List<String> categories, List<String> tags, String markdownContent, String htmlContent, String cssContent, String jsContent, String builderProjectJson, String seoTitle, String seoDescription,
                       PageTemplate template, PageStatus status) {
        this.title = required(title, "页面标题不能为空");
        this.slug = PageSlug.of(slug).value();
        this.summary = summary;
        this.excerpt = excerpt;
        this.coverImageUrl = coverImageUrl;
        this.categories = normalizeTerms(categories);
        this.tags = normalizeTerms(tags);
        this.markdownContent = markdownContent;
        this.htmlContent = htmlContent;
        this.cssContent = cssContent;
        this.jsContent = jsContent;
        this.builderProjectJson = builderProjectJson;
        this.seoTitle = seoTitle;
        this.seoDescription = seoDescription;
        this.template = template == null ? PageTemplate.DEFAULT : template;
        if (status == PageStatus.PUBLISHED) {
            publish();
        }
        else {
            this.status = PageStatus.DRAFT;
        }
    }

    public void publish() {
        this.status = PageStatus.PUBLISHED;
        if (this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }

    public void unpublish() {
        this.status = PageStatus.DRAFT;
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
