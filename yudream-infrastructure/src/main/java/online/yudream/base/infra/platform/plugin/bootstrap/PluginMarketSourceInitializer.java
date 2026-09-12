package online.yudream.base.infra.platform.plugin.bootstrap;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceType;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内置本机市场源播种。项目闸门关闭时不注册、不播种；能力开启后内置 {@code default} 源固定为 LOCAL
 * （本机发布物进程内直读）。远程源订阅不依赖本初始化器。Nexus 不再作为隐式默认源。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.plugin-market-source", name = "enabled", havingValue = "true")
public class PluginMarketSourceInitializer implements ApplicationRunner {

    public static final String BUILTIN_CODE = "default";
    public static final String BUILTIN_NAME = "本机插件市场";

    private final PluginMarketSourceRepo pluginMarketSourceRepo;

    @Override
    public void run(ApplicationArguments args) {
        PluginMarketSource existing = pluginMarketSourceRepo.findByCode(BUILTIN_CODE).orElse(null);
        if (existing == null) {
            pluginMarketSourceRepo.save(PluginMarketSource.builder()
                    .code(BUILTIN_CODE)
                    .name(BUILTIN_NAME)
                    .type(MarketSourceType.LOCAL)
                    .enabled(true)
                    .builtIn(true)
                    .sortOrder(0)
                    .build());
            return;
        }
        existing.setType(MarketSourceType.LOCAL);
        existing.setBuiltIn(true);
        existing.setRootUrl(null);
        if ("官方插件市场".equals(existing.getName())) {
            existing.setName(BUILTIN_NAME);
        }
        pluginMarketSourceRepo.save(existing);
    }
}
