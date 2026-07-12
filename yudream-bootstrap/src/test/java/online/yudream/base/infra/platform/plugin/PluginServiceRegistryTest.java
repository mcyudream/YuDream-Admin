package online.yudream.base.infra.platform.plugin;

import online.yudream.base.infra.platform.plugin.service.PluginServiceRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

class PluginServiceRegistryTest {

    @Test
    void returnsOnlyTheServiceExportedByTheRequestedPlugin() {
        PluginServiceRegistry registry = new PluginServiceRegistry();
        GreetingService walletService = name -> "wallet:" + name;
        GreetingService couponService = name -> "coupon:" + name;
        registry.export("wallet-plugin", GreetingService.class, walletService);
        registry.export("coupon-plugin", GreetingService.class, couponService);

        assertThat(registry.find("wallet-plugin", GreetingService.class)).containsSame(walletService);
        assertThat(registry.find("coupon-plugin", GreetingService.class)).containsSame(couponService);
    }

    @Test
    void removesAllExportsWhenProviderIsDisabled() {
        PluginServiceRegistry registry = new PluginServiceRegistry();
        registry.export("wallet-plugin", GreetingService.class, name -> name);

        registry.clear("wallet-plugin");

        assertThat(registry.find("wallet-plugin", GreetingService.class)).isEmpty();
    }

    @Test
    void findsServicesExportedByDifferentPluginsForTheSameApi() {
        PluginServiceRegistry registry = new PluginServiceRegistry();
        GreetingService walletService = name -> "wallet:" + name;
        GreetingService couponService = name -> "coupon:" + name;
        registry.export("wallet-plugin", GreetingService.class, walletService);
        registry.export("coupon-plugin", GreetingService.class, couponService);

        assertThat(registry.findAll(GreetingService.class)).containsExactlyInAnyOrderElementsOf(List.of(walletService, couponService));
    }

    private interface GreetingService {
        String greet(String name);
    }
}
