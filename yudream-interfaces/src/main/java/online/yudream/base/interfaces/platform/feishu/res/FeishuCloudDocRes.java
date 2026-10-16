package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 飞书云空间文件条目响应。
 */
@Data
@Builder
public class FeishuCloudDocRes implements Serializable {
    private String token;
    private String name;
    private String type;
    private String parentToken;
    private String url;
    private String shortcutTargetType;
    private String shortcutTargetToken;
    private String createdTime;
    private String modifiedTime;
    private String ownerId;
}
