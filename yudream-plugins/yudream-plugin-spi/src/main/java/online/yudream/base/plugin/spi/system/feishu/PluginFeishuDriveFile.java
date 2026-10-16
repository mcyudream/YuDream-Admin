package online.yudream.base.plugin.spi.system.feishu;

/**
 * 飞书云空间文件条目（drive/v1/files，只读视图）。
 *
 * <p>全部标识与时间保持飞书返回的字符串原样：token 可能超出 JavaScript 安全整数范围，
 * 时间戳不做本地格式化，避免精度与语义损失。</p>
 *
 * @param token                文件 token
 * @param name                 文件名
 * @param type                 文件类型（如 {@code docx}、{@code sheet}、{@code folder}、{@code file}）
 * @param parentToken          父目录 token；根目录为空串
 * @param url                  浏览器可打开的链接；取不到时为空串
 * @param shortcutTargetType   快捷方式目标类型；非快捷方式为空串
 * @param shortcutTargetToken  快捷方式目标 token；非快捷方式为空串
 * @param createdTime          创建时间（飞书原样字符串）；未知为空串
 * @param modifiedTime         最后修改时间（飞书原样字符串）；未知为空串
 * @param ownerId              所有者 ID（字符串保留精度）；未知为空串
 */
public record PluginFeishuDriveFile(
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
    public PluginFeishuDriveFile {
        token = token == null ? "" : token;
        name = name == null ? "" : name;
        type = type == null ? "" : type;
        parentToken = parentToken == null ? "" : parentToken;
        url = url == null ? "" : url;
        shortcutTargetType = shortcutTargetType == null ? "" : shortcutTargetType;
        shortcutTargetToken = shortcutTargetToken == null ? "" : shortcutTargetToken;
        createdTime = createdTime == null ? "" : createdTime;
        modifiedTime = modifiedTime == null ? "" : modifiedTime;
        ownerId = ownerId == null ? "" : ownerId;
    }
}
