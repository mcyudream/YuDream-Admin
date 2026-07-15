package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

final class PluginAgentManifestReader {

    List<Definition> read(InputStream inputStream) {
        if (inputStream == null) {
            throw new BizException("插件 JAR 缺少 plugin.yml");
        }
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(inputStream);
        if (!(loaded instanceof Map<?, ?> values)) {
            throw new BizException("plugin.yml 必须是 YAML 对象");
        }
        Object configured = values.get("agents");
        if (configured == null) {
            return List.of();
        }
        if (!(configured instanceof List<?> agents)) {
            throw new BizException("plugin.yml 的 agents 必须是列表");
        }
        return agents.stream().map(this::definition).toList();
    }

    private Definition definition(Object value) {
        if (!(value instanceof Map<?, ?> agent)) {
            throw new BizException("plugin.yml 的 agents 项必须是对象");
        }
        return new Definition(
                required(agent, "code"),
                required(agent, "name"),
                text(agent, "description"),
                text(agent, "icon"),
                required(agent, "systemPrompt"),
                required(agent, "workflow"),
                list(agent, "tools")
        );
    }

    private String required(Map<?, ?> values, String key) {
        String value = text(values, key);
        if (!StringUtils.hasText(value)) {
            throw new BizException("plugin.yml 的 Agent 缺少 " + key);
        }
        return value;
    }

    private String text(Map<?, ?> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private List<String> list(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new BizException("plugin.yml 的 Agent " + key + " 必须是列表");
        }
        return list.stream()
                .map(item -> item == null ? "" : String.valueOf(item).trim())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    record Definition(
            String code,
            String name,
            String description,
            String icon,
            String systemPrompt,
            String workflowResource,
            List<String> toolCodes
    ) {
    }
}
