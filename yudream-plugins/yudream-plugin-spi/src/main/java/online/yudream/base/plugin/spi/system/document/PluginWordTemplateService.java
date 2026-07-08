package online.yudream.base.plugin.spi.system.document;

import java.util.Map;
import java.util.List;
import java.util.Optional;

public interface PluginWordTemplateService {

    default boolean enabled() {
        return true;
    }

    default List<PluginWordTemplateSummary> templates(String keyword, int page, int size) {
        return List.of();
    }

    default Optional<PluginWordTemplateSummary> template(Long id) {
        return Optional.empty();
    }

    default Optional<PluginWordTemplateSummary> templateByCode(String code) {
        return Optional.empty();
    }

    PluginRenderedDocument render(byte[] templateContent, Map<String, Object> data);

    default PluginRenderedDocument render(Long templateId, Map<String, Object> data) {
        throw new IllegalArgumentException("Word 模板渲染能力不支持按模板 ID 渲染");
    }
}
