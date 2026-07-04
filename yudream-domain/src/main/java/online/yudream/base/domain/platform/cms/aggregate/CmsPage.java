package online.yudream.base.domain.platform.cms.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.valobj.PageSlug;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CmsPage extends BaseDomain {

    private String title;
    private String slug;
    private String summary;
    private String markdownContent;
    private String seoTitle;
    private String seoDescription;
    private PageStatus status;
    private LocalDateTime publishedAt;

    public static CmsPage create(String title, String slug) {
        CmsPage page = new CmsPage();
        page.title = required(title, "页面标题不能为空");
        page.slug = PageSlug.of(slug).value();
        page.status = PageStatus.DRAFT;
        return page;
    }

    public void update(String title, String slug, String summary, String markdownContent, String seoTitle,
                       String seoDescription, PageStatus status) {
        this.title = required(title, "页面标题不能为空");
        this.slug = PageSlug.of(slug).value();
        this.summary = summary;
        this.markdownContent = markdownContent;
        this.seoTitle = seoTitle;
        this.seoDescription = seoDescription;
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
}
