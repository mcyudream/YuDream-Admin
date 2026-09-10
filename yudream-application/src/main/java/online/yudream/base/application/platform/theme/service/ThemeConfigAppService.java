package online.yudream.base.application.platform.theme.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.theme.cmd.ThemeConfigSaveCmd;
import online.yudream.base.application.platform.theme.dto.ThemeConfigDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigFieldDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigOptionDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigSchemaDTO;
import online.yudream.base.application.platform.theme.dto.ThemeConfigSectionDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginThemeInfo;
import online.yudream.base.domain.platform.theme.service.ThemeConfigSecretCipher;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 主题配置编排：主题经 {@code @PluginTheme(configSchema=...)} 声明一份 JSON schema（分节 + 字段），
 * 宿主主题中心据此渲染独立配置大页面；配置值以 JSON 持久化到 Setting（key 前缀
 * {@code pluginTheme.config.}），公开站模板以 {@code theme.config.*} 消费。
 * 敏感字段（secret=true）加密存储、管理端脱敏、公开输出剔除；list 字段持久化为真数组，
 * 供模板 data-yb-for 遍历。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeConfigAppService {

    public static final String CATEGORY_PLUGIN_THEME = "plugin-theme";
    private static final String KEY_PREFIX = "pluginTheme.config.";
    private static final int MAX_LIST_ITEMS = 100;
    private static final Pattern FIELD_KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]{0,49}");
    private static final Set<String> FIELD_TYPES =
            Set.of("text", "textarea", "number", "switch", "select", "color", "image", "list");

    private final SettingRepo settingRepo;
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final ThemeConfigSecretCipher secretCipher;
    private final ObjectMapper objectMapper;

    /** schema 按 插件@资产版本 缓存，插件重载后版本变化自然失效。 */
    private final Map<String, ThemeConfigSchemaDTO> schemaCache = new ConcurrentHashMap<>();

    /**
     * 管理端读取：schema + 合并默认值后的当前值；敏感字段脱敏为空串，配置状态见 secretConfigured。
     * 内置默认主题与未声明 schema 的主题返回空 schema。
     */
    @Transactional(readOnly = true)
    public ThemeConfigDTO config(String themeCode) {
        ThemeConfigSchemaDTO schema = schema(themeCode).orElse(null);
        Map<String, Object> stored = storedValues(themeCode);
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Boolean> secretConfigured = new LinkedHashMap<>();
        if (schema != null) {
            for (ThemeConfigFieldDTO field : allFields(schema)) {
                if (isSecret(field)) {
                    Object storedValue = stored.get(field.getKey());
                    secretConfigured.put(field.getKey(),
                            storedValue instanceof String text && StringUtils.hasText(text));
                    values.put(field.getKey(), "");
                } else {
                    values.put(field.getKey(), stored.getOrDefault(field.getKey(), field.getDefaultValue()));
                }
            }
        }
        return ThemeConfigDTO.builder()
                .themeCode(themeCode)
                .schema(schema)
                .values(values)
                .secretConfigured(secretConfigured)
                .build();
    }

    /**
     * 保存配置：仅接受 schema 声明的键并按类型校验/收敛；敏感字段留空表示保持不变。
     * 未出现在载荷中的键保持原值，便于分节局部提交。
     */
    @Transactional
    public ThemeConfigDTO save(ThemeConfigSaveCmd cmd) {
        String themeCode = cmd.getThemeCode();
        ThemeConfigSchemaDTO schema = schema(themeCode)
                .orElseThrow(() -> new BizException("该主题未声明配置项"));
        Map<String, Object> incoming = cmd.getValues() == null ? Map.of() : cmd.getValues();
        Map<String, Object> merged = new LinkedHashMap<>(storedValues(themeCode));
        for (ThemeConfigFieldDTO field : allFields(schema)) {
            if (!incoming.containsKey(field.getKey())) {
                continue;
            }
            Object coerced = coerce(field, incoming.get(field.getKey()));
            if (isSecret(field)) {
                String plain = coerced == null ? "" : String.valueOf(coerced);
                if (!StringUtils.hasText(plain)) {
                    continue;
                }
                if (!secretCipher.canEncrypt()) {
                    throw new BizException("未配置凭据密钥，无法保存敏感配置");
                }
                merged.put(field.getKey(), secretCipher.encrypt(themeCode, field.getKey(), plain));
            } else if (coerced == null) {
                merged.remove(field.getKey());
            } else {
                merged.put(field.getKey(), coerced);
            }
        }
        persist(themeCode, merged);
        return config(themeCode);
    }

    /**
     * 公开站消费的配置：默认值与已存值合并、剔除敏感字段，list 为真数组。
     * 任何 schema/解析异常都不允许击穿公开页渲染，降级为空配置。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> publicConfig(String themeCode) {
        try {
            ThemeConfigSchemaDTO schema = schema(themeCode).orElse(null);
            if (schema == null) {
                return Map.of();
            }
            Map<String, Object> stored = storedValues(themeCode);
            Map<String, Object> result = new LinkedHashMap<>();
            for (ThemeConfigFieldDTO field : allFields(schema)) {
                if (isSecret(field)) {
                    continue;
                }
                result.put(field.getKey(), stored.getOrDefault(field.getKey(), field.getDefaultValue()));
            }
            return result;
        } catch (Exception e) {
            log.warn("主题公开配置解析失败，降级为空配置：theme={}, reason={}", themeCode, e.getMessage());
            return Map.of();
        }
    }

    private Optional<ThemeConfigSchemaDTO> schema(String themeCode) {
        if (!StringUtils.hasText(themeCode) || HomePageLayout.DEFAULT_THEME_CODE.equals(themeCode)) {
            return Optional.empty();
        }
        Optional<PluginThemeInfo> theme = pluginRuntimeGateway.themes().stream()
                .filter(item -> themeCode.equals(item.code()) || themeCode.equals(item.pluginCode()))
                .findFirst();
        if (theme.isEmpty() || !StringUtils.hasText(theme.get().configSchema())) {
            return Optional.empty();
        }
        PluginThemeInfo info = theme.get();
        String cacheKey = info.pluginCode() + "@" + info.assetRevision();
        return Optional.ofNullable(schemaCache.computeIfAbsent(cacheKey, key -> loadSchema(info)));
    }

    private ThemeConfigSchemaDTO loadSchema(PluginThemeInfo theme) {
        return pluginRuntimeGateway.frontendAsset(theme.pluginCode(), theme.configSchema())
                .map(asset -> {
                    try {
                        ThemeConfigSchemaDTO schema = objectMapper.readValue(
                                new String(asset.body(), StandardCharsets.UTF_8), ThemeConfigSchemaDTO.class);
                        validateSchema(theme.pluginCode(), schema);
                        return schema;
                    } catch (BizException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new BizException("主题配置 schema 解析失败：" + e.getMessage());
                    }
                })
                .orElseThrow(() -> new BizException("主题配置 schema 资产不存在：" + theme.configSchema()));
    }

    private void validateSchema(String pluginCode, ThemeConfigSchemaDTO schema) {
        List<ThemeConfigSectionDTO> sections = schema.getSections() == null ? List.of() : schema.getSections();
        Set<String> keys = new LinkedHashSet<>();
        for (ThemeConfigSectionDTO section : sections) {
            for (ThemeConfigFieldDTO field : section.getFields() == null ? List.<ThemeConfigFieldDTO>of() : section.getFields()) {
                validateField(pluginCode, field, false);
                if (!keys.add(field.getKey())) {
                    throw new BizException("主题配置 schema 字段键重复：" + field.getKey());
                }
            }
        }
    }

    private void validateField(String pluginCode, ThemeConfigFieldDTO field, boolean listItem) {
        if (!StringUtils.hasText(field.getKey()) || !FIELD_KEY.matcher(field.getKey()).matches()) {
            throw new BizException("主题配置 schema 字段键非法：" + field.getKey());
        }
        if (!StringUtils.hasText(field.getType()) || !FIELD_TYPES.contains(field.getType())) {
            throw new BizException("主题配置 schema 字段类型非法：" + field.getKey());
        }
        if (listItem && isSecret(field)) {
            throw new BizException("主题配置 schema 列表子字段暂不支持敏感配置：" + field.getKey());
        }
        if ("list".equals(field.getType())) {
            if (field.getItemFields() == null || field.getItemFields().isEmpty()) {
                throw new BizException("主题配置 schema 列表字段缺少子字段定义：" + field.getKey());
            }
            for (ThemeConfigFieldDTO itemField : field.getItemFields()) {
                validateField(pluginCode, itemField, true);
            }
        }
        if ("select".equals(field.getType()) && (field.getOptions() == null || field.getOptions().isEmpty())) {
            throw new BizException("主题配置 schema 下拉字段缺少可选项：" + field.getKey());
        }
    }

    private List<ThemeConfigFieldDTO> allFields(ThemeConfigSchemaDTO schema) {
        List<ThemeConfigFieldDTO> fields = new ArrayList<>();
        for (ThemeConfigSectionDTO section : schema.getSections() == null ? List.<ThemeConfigSectionDTO>of() : schema.getSections()) {
            if (section.getFields() != null) {
                fields.addAll(section.getFields());
            }
        }
        return fields;
    }

    private boolean isSecret(ThemeConfigFieldDTO field) {
        return Boolean.TRUE.equals(field.getSecret());
    }

    private Object coerce(ThemeConfigFieldDTO field, Object value) {
        return switch (field.getType()) {
            case "number" -> coerceNumber(field, value);
            case "switch" -> coerceSwitch(value);
            case "list" -> coerceList(field, value);
            case "select" -> coerceSelect(field, value);
            default -> value == null ? null : String.valueOf(value);
        };
    }

    private Object coerceNumber(ThemeConfigFieldDTO field, Object value) {
        if (value == null || (value instanceof String text && !StringUtils.hasText(text))) {
            return null;
        }
        if (value instanceof Number number) {
            return number;
        }
        String text = String.valueOf(value);
        try {
            if (text.contains(".")) {
                return Double.parseDouble(text);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new BizException("主题配置字段需为数值：" + field.getKey());
        }
    }

    private Object coerceSwitch(Object value) {
        if (value == null) {
            return Boolean.FALSE;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Object coerceSelect(ThemeConfigFieldDTO field, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        List<String> allowed = field.getOptions() == null ? List.of() : field.getOptions().stream()
                .map(ThemeConfigOptionDTO::getValue)
                .toList();
        if (!allowed.isEmpty() && !allowed.contains(text)) {
            throw new BizException("主题配置字段取值不在可选项内：" + field.getKey());
        }
        return text;
    }

    private Object coerceList(ThemeConfigFieldDTO field, Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> items)) {
            throw new BizException("主题配置字段需为数组：" + field.getKey());
        }
        if (items.size() > MAX_LIST_ITEMS) {
            throw new BizException("主题配置列表超出上限（" + MAX_LIST_ITEMS + " 项）：" + field.getKey());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new BizException("主题配置列表项需为对象：" + field.getKey());
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            for (ThemeConfigFieldDTO itemField : field.getItemFields()) {
                Object coerced = coerce(itemField, raw.get(itemField.getKey()));
                if (coerced != null) {
                    entry.put(itemField.getKey(), coerced);
                }
            }
            result.add(entry);
        }
        return result;
    }

    private Map<String, Object> storedValues(String themeCode) {
        if (!StringUtils.hasText(themeCode)) {
            return Map.of();
        }
        return settingRepo.findByKey(KEY_PREFIX + themeCode)
                .map(Setting::getValue)
                .filter(StringUtils::hasText)
                .map(this::readValues)
                .orElseGet(Map::of);
    }

    private Map<String, Object> readValues(String json) {
        try {
            Map<String, Object> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return values == null ? Map.of() : values;
        } catch (Exception e) {
            log.warn("主题配置持久化值解析失败，按空配置处理：reason={}", e.getMessage());
            return Map.of();
        }
    }

    private void persist(String themeCode, Map<String, Object> values) {
        String key = KEY_PREFIX + themeCode;
        Setting setting = settingRepo.findByKey(key).orElseGet(() -> Setting.builder().key(key).build());
        try {
            setting.setValue(objectMapper.writeValueAsString(values));
        } catch (JsonProcessingException e) {
            throw new BizException("主题配置序列化失败");
        }
        setting.setType(SettingType.JSON);
        setting.setCategory(CATEGORY_PLUGIN_THEME);
        setting.setDescription("主题配置：" + themeCode);
        settingRepo.save(setting);
    }
}
