package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
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
                optionalGitUrl(values)
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
