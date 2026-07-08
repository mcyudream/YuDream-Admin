package online.yudream.base.plugin.spi.system.document;

import java.util.Map;

public interface PluginWordTemplateService {

    PluginRenderedDocument render(byte[] templateContent, Map<String, Object> data);
}
