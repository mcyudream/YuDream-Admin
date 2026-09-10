package online.yudream.base.application.platform.cms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;

import java.io.Serial;
import java.io.Serializable;

/**
 * 插件主题自带页面集里的单个页面载荷。除 slug/title 外均可空；
 * 页面 HTML 约定使用 data-yb-* 模板指令注入系统数据，导航由系统统一渲染，
 * 主题不得自带 navigationJson。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginPresetPagePayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String slug;
    private String title;
    private String summary;
    private String coverImageUrl;
    private PageTemplate template;
    private String markdownContent;
    private String htmlContent;
    private String cssContent;
    private String jsContent;
    private String seoTitle;
    private String seoDescription;
}
