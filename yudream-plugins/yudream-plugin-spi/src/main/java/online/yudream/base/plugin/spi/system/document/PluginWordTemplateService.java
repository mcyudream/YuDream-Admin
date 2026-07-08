package online.yudream.base.plugin.spi.system.document;

import java.util.Map;

public interface PluginWordTemplateService {

    default boolean enabled() {
        return true;
    }

    PluginRenderedDocument render(byte[] templateContent, Map<String, Object> data);
}
