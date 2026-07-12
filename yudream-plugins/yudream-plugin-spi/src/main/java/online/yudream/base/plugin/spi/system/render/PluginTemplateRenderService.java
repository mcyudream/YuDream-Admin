package online.yudream.base.plugin.spi.system.render;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface PluginTemplateRenderService {

    default CompletionStage<PluginRenderedImage> render(String templateName, Map<String, Object> variables) {
        return render(templateName, variables, null);
    }

    CompletionStage<PluginRenderedImage> render(String templateName, Map<String, Object> variables, String selector);
}
