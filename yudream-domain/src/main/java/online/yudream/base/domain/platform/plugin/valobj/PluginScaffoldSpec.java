package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 插件脚手架生成规格：开发者工具「新建插件」向导的领域入参。
 * code 采用 kebab-case（与 plugin.yml 的 name 一致）；包名段按官方插件惯例去掉连字符
 * （如 qq-binding → qqbinding），类名为各段首字母大写拼接后加 Plugin 后缀。
 */
public record PluginScaffoldSpec(
        String code,
        String displayName,
        String description,
        String version,
        String spiVersion,
        String parentDir,
        List<String> depend,
        List<String> softdepend
) {

    public static final String ROOT_PACKAGE = "online.yudream.base.plugin";
    public static final String DEFAULT_VERSION = "1.0.0";
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?$");
    private static final int CODE_MAX_LENGTH = 32;

    public static PluginScaffoldSpec of(String code, String displayName, String description, String version,
                                        String spiVersion, String parentDir,
                                        List<String> depend, List<String> softdepend) {
        if (!hasText(code)) {
            throw new BizException("插件编码不能为空");
        }
        String trimmedCode = code.trim();
        if (trimmedCode.length() > CODE_MAX_LENGTH || !CODE_PATTERN.matcher(trimmedCode).matches()) {
            throw new BizException("插件编码需为小写字母开头的 kebab-case（字母/数字/单连字符）：" + trimmedCode);
        }
        if (!hasText(parentDir)) {
            throw new BizException("目标父目录不能为空");
        }
        String trimmedVersion = hasText(version) ? version.trim() : DEFAULT_VERSION;
        if (!VERSION_PATTERN.matcher(trimmedVersion).matches()) {
            throw new BizException("版本号需为 x.y.z 形式：" + trimmedVersion);
        }
        List<String> hard = normalizeCodes(depend, "硬依赖");
        List<String> soft = normalizeCodes(softdepend, "软依赖");
        if (hard.contains(trimmedCode) || soft.contains(trimmedCode)) {
            throw new BizException("插件不能依赖自身：" + trimmedCode);
        }
        for (String hardCode : hard) {
            if (soft.contains(hardCode)) {
                throw new BizException("插件 " + hardCode + " 不能同时是硬依赖与软依赖");
            }
        }
        return new PluginScaffoldSpec(
                trimmedCode,
                hasText(displayName) ? displayName.trim() : trimmedCode,
                description == null ? "" : description.trim(),
                trimmedVersion,
                hasText(spiVersion) ? spiVersion.trim() : null,
                parentDir.trim(),
                hard,
                soft);
    }

    /** 包名根下的插件段：连字符去掉（wordle → wordle，qq-binding → qqbinding）。 */
    public String packageSegment() {
        return code.replace("-", "");
    }

    public String basePackage() {
        return ROOT_PACKAGE + "." + packageSegment();
    }

    /** 入口类简单名：各段首字母大写拼接 + Plugin（qq-binding → QqBindingPlugin）。 */
    public String entryClassName() {
        StringBuilder name = new StringBuilder();
        for (String segment : code.split("-")) {
            name.append(Character.toUpperCase(segment.charAt(0)));
            if (segment.length() > 1) {
                name.append(segment.substring(1));
            }
        }
        return name.append("Plugin").toString();
    }

    public String mainClass() {
        return basePackage() + ".bootstrap." + entryClassName();
    }

    /** 模块目录名：与官方插件仓一致，yudream-plugin-{code}。 */
    public String moduleDirName() {
        return "yudream-plugin-" + code;
    }

    private static List<String> normalizeCodes(List<String> codes, String label) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(PluginScaffoldSpec::hasText)
                .map(String::trim)
                .distinct()
                .peek(item -> {
                    if (item.length() > CODE_MAX_LENGTH || !CODE_PATTERN.matcher(item).matches()) {
                        throw new BizException(label + "编码非法：" + item);
                    }
                })
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
