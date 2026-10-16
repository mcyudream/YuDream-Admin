package online.yudream.base.domain.platform.feishu.valobj;

/**
 * 飞书云空间文件条目（drive/v1/files）。
 * <p>
 * 全部标识与时间保持飞书返回的字符串原样：token 可能超出 JavaScript 安全整数范围，
 * 时间戳不做本地化解析，避免精度与语义损失。
 */
public record FeishuCloudDoc(
        String token,
        String name,
        String type,
        String parentToken,
        String url,
        String shortcutTargetType,
        String shortcutTargetToken,
        String createdTime,
        String modifiedTime,
        String ownerId
) {
}
