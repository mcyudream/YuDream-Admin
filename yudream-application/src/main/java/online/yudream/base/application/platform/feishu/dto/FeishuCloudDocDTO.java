package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 飞书云空间文件条目。全部标识与时间保持飞书原样字符串，避免长 ID 精度损失。
 */
@Data
@Builder
public class FeishuCloudDocDTO {
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
