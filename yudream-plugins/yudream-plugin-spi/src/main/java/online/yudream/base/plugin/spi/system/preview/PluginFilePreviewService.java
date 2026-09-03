package online.yudream.base.plugin.spi.system.preview;

/**
 * 平台文件预览能力端口。
 * <p>
 * 文件预览是平台「双闸门」能力模块（能力编码 {@value #CAPABILITY_CODE}）：
 * 部署侧由环境变量 {@code PLATFORM_FILE_PREVIEW_ENABLED} 决定是否提供该能力，
 * 运行侧由管理员在管理后台「平台能力 &gt; 文件预览」启用并维护 kkFileView 配置（入库、可改）。
 * 平台为插件文件签发短时效公开访问地址，并给出 KKFILE / DIRECT / NONE 预览决策；
 * 插件不再各自维护预览引擎配置与签名端点。
 * 默认实现表示宿主未提供该能力（{@code enabled()} 恒 false、预览恒为 NONE）。
 */
public interface PluginFilePreviewService {

    /** 平台能力模块编码（「平台能力」页与 {@code yudream.platform.capabilities.file-preview.*} 配置前缀）。 */
    String CAPABILITY_CODE = "file-preview";
    /** 能力配置键：kkFileView 服务地址（浏览器可达，如 {@code http(s)://站点/kkfileview}）。 */
    String CONFIG_BASE_URL = "baseUrl";
    /** 能力配置键：预览回源基址（kkFileView 服务器可达的宿主地址）；空表示从请求推导。 */
    String CONFIG_CALLBACK_BASE_URL = "callbackBaseUrl";
    /** 能力配置键：office 预览类型（追加 {@code &officePreviewType=}，如 pdf）；空表示不追加。 */
    String CONFIG_OFFICE_PREVIEW_TYPE = "officePreviewType";
    /** 能力配置键：签名文件地址时效（秒，60 ~ 86400）。 */
    String CONFIG_TOKEN_TTL_SECONDS = "tokenTtlSeconds";
    /** 能力配置键：预览大小上限（MB，1 ~ 2048）。 */
    String CONFIG_MAX_PREVIEW_SIZE_MB = "maxPreviewSizeMb";

    long DEFAULT_TOKEN_TTL_SECONDS = 1800L;
    long DEFAULT_MAX_PREVIEW_SIZE_MB = 200L;
    String DEFAULT_OFFICE_PREVIEW_TYPE = "pdf";

    /** kkFileView 是否已启用且配置完整；启用时所有可转码格式都走 KKFILE。 */
    default boolean enabled() {
        return false;
    }

    /** 当前生效的 kkFileView 服务地址；空串表示未配置。 */
    default String kkFileViewBaseUrl() {
        return "";
    }

    /** 当前生效的 office 预览类型；空串表示不追加 {@code officePreviewType} 参数。 */
    default String officePreviewType() {
        return "";
    }

    /** 签名文件地址时效（秒）。 */
    default long tokenTtlSeconds() {
        return DEFAULT_TOKEN_TTL_SECONDS;
    }

    /** 预览大小上限（MB）。 */
    default long maxPreviewSizeMb() {
        return DEFAULT_MAX_PREVIEW_SIZE_MB;
    }

    /** 预览大小上限（字节）；0 表示宿主未提供该能力。 */
    default long maxPreviewSizeBytes() {
        return 0L;
    }

    /**
     * 配置的预览回源基址（kkFileView 服务器可达的宿主地址）；空串表示未配置，
     * 调用方需从当前请求推导（Host / X-Forwarded-Proto）。
     */
    default String callbackBaseUrl() {
        return "";
    }

    /**
     * 把 kkFileView 可达的绝对文件地址包装为 kkFileView iframe 绝对地址；
     * 未启用或地址为空时返回空串。
     */
    default String kkFileViewUrl(String absoluteFileUrl) {
        return "";
    }

    /**
     * 签发插件文件的短时效公开访问地址（{@code /api/} 开头的宿主相对地址，
     * 前端用 {@code sdk.files.assetUrl(url)} 解析），用于 {@code <img src>} 等
     * 无法携带鉴权头的裸引用场景。签发失败（能力缺失、参数不合法）返回空串。
     */
    default String signedFileUrl(String pluginCode, String objectKey, String filename) {
        return "";
    }

    /**
     * 为插件文件构建预览：KKFILE = iframe 绝对地址（回源走平台签名端点）；
     * DIRECT = 签名文件地址（浏览器可直读格式）；NONE = 不可预览。
     */
    default PluginPreviewInfo preview(String pluginCode, PluginPreviewFile file) {
        return PluginPreviewInfo.none("平台未提供文件预览能力");
    }

    /**
     * 为自带凭证的外部绝对地址（如分享外链文件端点）构建预览：
     * KKFILE = iframe 绝对地址；DIRECT = 原地址（浏览器可直读格式）；NONE = 不可预览。
     */
    default PluginPreviewInfo previewExternal(String absoluteFileUrl, String ext, long size) {
        return PluginPreviewInfo.none("平台未提供文件预览能力");
    }
}
