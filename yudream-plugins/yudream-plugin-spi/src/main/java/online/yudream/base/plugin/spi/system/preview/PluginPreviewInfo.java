package online.yudream.base.plugin.spi.system.preview;

/**
 * 平台预览决策结果。
 * <ul>
 *   <li>{@code KKFILE}：url 为 kkFileView 完整 iframe 绝对地址，前端直接 {@code <iframe :src>}。</li>
 *   <li>{@code DIRECT}：url 为可直接访问的文件地址——宿主内为 {@code /api/} 开头的相对地址
 *       （前端用 {@code sdk.files.assetUrl(url)} 解析，浏览器原生 img/video/audio/iframe 渲染），
 *       或自带凭证的绝对地址（分享外链场景）。</li>
 *   <li>{@code NONE}：不可预览，message 为面向用户的提示。</li>
 * </ul>
 */
public record PluginPreviewInfo(String mode, String url, String message) {

    public static final String MODE_KKFILE = "KKFILE";
    public static final String MODE_DIRECT = "DIRECT";
    public static final String MODE_NONE = "NONE";

    public static PluginPreviewInfo kkfile(String url) {
        return new PluginPreviewInfo(MODE_KKFILE, url, null);
    }

    public static PluginPreviewInfo direct(String url) {
        return new PluginPreviewInfo(MODE_DIRECT, url, null);
    }

    public static PluginPreviewInfo none(String message) {
        return new PluginPreviewInfo(MODE_NONE, null, message);
    }
}
