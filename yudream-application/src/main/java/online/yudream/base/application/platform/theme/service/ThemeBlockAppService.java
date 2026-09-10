package online.yudream.base.application.platform.theme.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.plugin.spi.system.extension.PluginExtensionQuery;
import online.yudream.base.plugin.spi.theme.PluginThemeBlockContext;
import online.yudream.base.plugin.spi.theme.PluginThemeBlockProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 主题数据块解析：仅加载页面显式请求的块，按激活 SITE 主题过滤提供者的 supportedThemes，
 * 逐块异常隔离（失败块直接缺省，消费侧自然降级）。
 * 扩展注册表只含已启用插件，禁用/卸载即自动回收，无需额外过滤。
 * CMS 模板上下文与公开主题上下文共用此端口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeBlockAppService {

    private static final int DEFAULT_LIMIT = 12;
    private static final int MAX_LIST_LIMIT = 50;
    private static final int MAX_BLOCKS = 12;
    private static final Pattern BLOCK_CODE = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

    private final PluginExtensionQuery pluginExtensionQuery;
    private final ObjectMapper objectMapper;

    /**
     * 解析请求的主题块：theme 为当前激活 SITE 主题编码，limit 约束单块数据量。
     * 返回块编码到 JSON 安全数据的映射；未请求或无可用提供者时为空表。
     */
    public Map<String, Object> resolveBlocks(List<String> requested, String theme, Integer blockLimit) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        List<PluginThemeBlockProvider> providers = pluginExtensionQuery.extensions(PluginThemeBlockProvider.class);
        if (providers.isEmpty()) {
            return Map.of();
        }
        int limit = bounded(blockLimit);
        Map<String, Object> blocks = new LinkedHashMap<>();
        for (String code : new LinkedHashSet<>(requested)) {
            if (blocks.size() >= MAX_BLOCKS) {
                break;
            }
            if (code == null || !BLOCK_CODE.matcher(code).matches()) {
                continue;
            }
            providers.stream()
                    .filter(provider -> code.equals(provider.code()))
                    .filter(provider -> provider.supportedThemes().isEmpty() || provider.supportedThemes().contains(theme))
                    .findFirst()
                    .ifPresent(provider -> {
                        try {
                            Object data = provider.data(new PluginThemeBlockContext(theme, limit));
                            if (data != null) {
                                blocks.put(code, objectMapper.convertValue(data, Object.class));
                            }
                        } catch (Exception e) {
                            log.warn("主题块数据解析失败：block={}, reason={}", code, e.getMessage());
                        }
                    });
        }
        return blocks;
    }

    private int bounded(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested <= 0) {
            return 0;
        }
        return Math.min(requested, MAX_LIST_LIMIT);
    }
}
