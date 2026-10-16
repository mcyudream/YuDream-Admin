package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 飞书知识库空间条目响应。
 */
@Data
@Builder
public class FeishuWikiSpaceRes implements Serializable {
    private String spaceId;
    private String name;
    private String description;
    private String spaceType;
    private String visibility;
    private String openSharing;
}
