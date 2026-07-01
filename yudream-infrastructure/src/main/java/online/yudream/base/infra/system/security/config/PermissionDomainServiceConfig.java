package online.yudream.base.infra.system.security.config;

import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 权限领域服务 Bean 注册配置。
 */
@Configuration
public class PermissionDomainServiceConfig {

    @Bean
    public PermissionDomainService permissionDomainService(PermissionRepo permissionRepo) {
        return new PermissionDomainService(permissionRepo);
    }
}
