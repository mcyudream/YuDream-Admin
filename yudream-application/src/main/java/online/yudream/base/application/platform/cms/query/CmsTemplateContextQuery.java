package online.yudream.base.application.platform.cms.query;

import lombok.Data;

import java.util.List;

/**
 * CMS 模板上下文读取请求。各字段为空时使用模板协议默认值，应用层统一限制最大返回条数。
 * blocks 为模板声明引用的主题块 code 列表（data-yb-* 中的 blocks.{code} 路径），
 * 应用层按激活 SITE 主题过滤插件注册的块提供者并惰性求值。
 */
@Data
public class CmsTemplateContextQuery {
    private Integer cmsLatestLimit;
    private Integer knowledgeSpacesLimit;
    private Integer knowledgePagesLimit;
    private Integer knowledgeLatestLimit;
    private Integer knowledgeFeaturedLimit;
    /** 模板引用的主题块 code 列表（blocks.{code}），空表示不加载任何块。 */
    private List<String> blocks;
    /** 块数据条数上限（传递给块提供者），空时使用默认值。 */
    private Integer blockLimit;
}
