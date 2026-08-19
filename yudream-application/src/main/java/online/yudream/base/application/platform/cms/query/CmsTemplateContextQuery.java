package online.yudream.base.application.platform.cms.query;

import lombok.Data;

/**
 * CMS 模板上下文读取请求。各字段为空时使用模板协议默认值，应用层统一限制最大返回条数。
 */
@Data
public class CmsTemplateContextQuery {
    private Integer cmsLatestLimit;
    private Integer knowledgeSpacesLimit;
    private Integer knowledgePagesLimit;
    private Integer knowledgeLatestLimit;
    private Integer knowledgeFeaturedLimit;
}
