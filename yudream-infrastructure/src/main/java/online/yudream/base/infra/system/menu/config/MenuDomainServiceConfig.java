package online.yudream.base.infra.system.menu.config;

import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 菜单领域服务 Bean 注册配置。
 */
@Configuration
@EnableConfigurationProperties(SystemSeedProperties.class)
public class MenuDomainServiceConfig {

    @Bean
    public MenuDomainService menuDomainService(MenuRepo menuRepo, PermissionRepo permissionRepo) {
        return new MenuDomainService(menuRepo, permissionRepo);
    }
}
