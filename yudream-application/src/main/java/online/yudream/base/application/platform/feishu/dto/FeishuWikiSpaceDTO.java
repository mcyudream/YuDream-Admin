package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 飞书知识库空间条目。
 */
@Data
@Builder
public class FeishuWikiSpaceDTO {
    private String spaceId;
    private String name;
    private String description;
    private String spaceType;
    private String visibility;
    private String openSharing;
}
