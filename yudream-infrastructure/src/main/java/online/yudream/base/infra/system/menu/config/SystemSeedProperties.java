package online.yudream.base.infra.system.menu.config;

import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.SeedSyncMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yudream.system.seed")
public class SystemSeedProperties {

    private MenuSeedProperties menu = new MenuSeedProperties();

    @Data
    public static class MenuSeedProperties {
        private SeedSyncMode syncMode = SeedSyncMode.MISSING_ONLY;
    }
}
