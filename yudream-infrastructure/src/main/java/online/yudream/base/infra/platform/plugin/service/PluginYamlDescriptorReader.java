package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import online.yudream.base.plugin.spi.core.PluginMobileSupport;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class PluginYamlDescriptorReader {

    public PluginDescriptor read(InputStream inputStream) {
        if (inputStream == null) {
            throw new BizException("插件 JAR 缺少 plugin.yml");
        }
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(inputStream);
        if (!(loaded instanceof Map<?, ?> values)) {
            throw new BizException("plugin.yml 必须是 YAML 对象");
        }
        String code = required(values, "name");
        String displayName = value(values, "displayName");
        return new PluginDescriptor(
                code,
                StringUtils.hasText(displayName) ? displayName : code,
                required(values, "version"),
                value(values, "description"),
                required(values, "main"),
                list(values, "depend"),
                list(values, "softdepend"),
                optionalIcon(values),
                optionalGitUrl(values),
                optionalMobileSupport(values)
        );
    }

    private String required(Map<?, ?> values, String key) {
        String value = value(values, key);
        if (!StringUtils.hasText(value)) {
            throw new BizException("plugin.yml 缺少 " + key);
        }
        return value;
    }

    private String value(Map<?, ?> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String optionalIcon(Map<?, ?> values) {
        String icon = value(values, "icon");
        if (!StringUtils.hasText(icon)) {
            return null;
        }
        if (icon.contains("..") || icon.startsWith("/") || icon.matches("^[A-Za-z]:.*")) {
            throw new BizException("plugin.yml 的 icon 路径无效");
        }
        return icon;
    }

    /** plugin.yml 可选 git 字段：源码仓库地址，仅接受 http(s) URL。 */
    private String optionalGitUrl(Map<?, ?> values) {
        String gitUrl = value(values, "git");
        if (!StringUtils.hasText(gitUrl)) {
            return null;
        }
        if (gitUrl.length() > 200 || !(gitUrl.startsWith("http://") || gitUrl.startsWith("https://"))
                || gitUrl.contains("..") || gitUrl.matches(".*[\\s<>\"'].*")) {
            throw new BizException("plugin.yml 的 git 必须是合法的 http(s) 仓库地址");
        }
        return gitUrl;
    }

    /**
     * plugin.yml 可选 mobile 块：platforms（缺省 android+ios）、minHostVersion（缺省 1.0.0）、
     * requiredNativeCapabilities（缺省空）。注册期校验合法性：platforms 只允许 android/ios。
     */
    private PluginMobileSupport optionalMobileSupport(Map<?, ?> values) {
        Object mobile = values.get("mobile");
        if (mobile == null) {
            return null;
        }
        if (!(mobile instanceof Map<?, ?> mobileValues)) {
            throw new BizException("plugin.yml 的 mobile 必须是 YAML 对象");
        }
        List<String> platforms = list(mobileValues, "platforms");
        for (String platform : platforms) {
            if (!"android".equalsIgnoreCase(platform) && !"ios".equalsIgnoreCase(platform)) {
                throw new BizException("plugin.yml 的 mobile.platforms 仅支持 android/ios：" + platform);
            }
        }
        String minHostVersion = value(mobileValues, "minHostVersion");
        if (StringUtils.hasText(minHostVersion) && !minHostVersion.matches("[0-9]+(\\.[0-9]+){0,3}(-[0-9A-Za-z.-]+)?")) {
            throw new BizException("plugin.yml 的 mobile.minHostVersion 必须是语义化版本：" + minHostVersion);
        }
        return new PluginMobileSupport(
                platforms,
                minHostVersion,
                list(mobileValues, "requiredNativeCapabilities")
        );
    }

    private List<String> list(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new BizException("plugin.yml 的 " + key + " 必须是列表");
        }
        return list.stream()
                .map(item -> item == null ? "" : String.valueOf(item).trim())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
