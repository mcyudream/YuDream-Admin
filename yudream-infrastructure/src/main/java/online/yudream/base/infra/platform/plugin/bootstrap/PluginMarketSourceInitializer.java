package online.yudream.base.infra.platform.plugin.bootstrap;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceRepo;
import online.yudream.base.infra.platform.plugin.service.PluginProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 内置官方市场源播种。rootUrl 始终镜像 yudream.platform.plugin.store-root-url 配置，
 * 名称/令牌/启停归管理端，不覆盖；项目闸门关闭时不播种，市场走配置直连。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.plugin-market-source", name = "enabled", havingValue = "true")
public class PluginMarketSourceInitializer implements ApplicationRunner {

    public static final String BUILTIN_CODE = "default";

    private final PluginMarketSourceRepo pluginMarketSourceRepo;
    private final PluginProperties pluginProperties;

    @Override
    public void run(ApplicationArguments args) {
        String rootUrl = pluginProperties.getStoreRootUrl();
        if (!StringUtils.hasText(rootUrl)) {
            return;
        }
        PluginMarketSource existing = pluginMarketSourceRepo.findByCode(BUILTIN_CODE).orElse(null);
        if (existing == null) {
            pluginMarketSourceRepo.save(PluginMarketSource.builder()
                    .code(BUILTIN_CODE)
                    .name("官方插件市场")
                    .rootUrl(rootUrl)
                    .enabled(true)
                    .builtIn(true)
                    .sortOrder(0)
                    .build());
            return;
        }
        existing.setRootUrl(rootUrl);
        pluginMarketSourceRepo.save(existing);
    }
}
