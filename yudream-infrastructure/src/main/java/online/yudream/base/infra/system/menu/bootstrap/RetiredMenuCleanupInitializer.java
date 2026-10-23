package online.yudream.base.infra.system.menu.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 退役菜单清理器。
 * <p>
 * 菜单种子默认 MISSING_ONLY：从 {@code SystemMenuModule} 移除声明不会删除已落库的历史节点。
 * 这里按精确 code 列表移除已退役的 SYSTEM 菜单及其按钮（当前为曾短暂提供的
 * 「数据模板」独立页面），权限目录随 {@link MenuDomainService#deleteMenu} 一并收敛。
 * 插件菜单与用户自建菜单不受影响；列表之外的任何 code 都不会被触碰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 90)
public class RetiredMenuCleanupInitializer implements ApplicationListener<ApplicationReadyEvent> {

    /** 已退役的种子菜单 code；新增退役入口时在此登记。 */
    private static final List<String> RETIRED_CODES = List.of(
            "system:template",
            "system:template:view",
            "system:template:export",
            "system:template:import"
    );

    private final MenuDomainService menuDomainService;
    private final online.yudream.base.domain.system.menu.repo.MenuRepo menuRepo;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        int removed = 0;
        for (String code : RETIRED_CODES) {
            if (menuRepo.findByCode(code).isEmpty()) {
                continue;
            }
            try {
                menuDomainService.deleteMenu(code);
                removed++;
            }
            catch (RuntimeException e) {
                log.warn("退役菜单清理失败，保留现场等待人工处理: code={}, reason={}", code, e.getMessage());
            }
        }
        if (removed > 0) {
            log.info("Removed {} retired system menu nodes", removed);
        }
    }
}
