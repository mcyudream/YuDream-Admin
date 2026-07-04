package online.yudream.base.plugin.spi.frontend;

import java.util.List;

public record PluginFrontendModule(
        String entry,
        String moduleName,
        String sdkVersion,
        String integrity,
        List<PluginFrontendRoute> routes
) {
    public PluginFrontendModule {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
