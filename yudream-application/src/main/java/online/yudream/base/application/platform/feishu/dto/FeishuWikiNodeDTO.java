package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 飞书知识库节点条目。
 * <p>
 * 标识与文本字段与飞书返回保持字符串原样（长 ID 不转数值）。
 */
@Data
@Builder
public class FeishuWikiNodeDTO {
    private String nodeToken;
    private String objToken;
    private String objType;
    private String title;
    private boolean hasChild;
    private String parentNodeToken;
    private String nodeType;
    private String originNodeToken;
    private String originSpaceId;
    private String spaceId;
}
