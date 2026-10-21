package online.yudream.base.infra.platform.cms.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.cms.service.StudioAppService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 框架内置主题（default）的常规站点启动初始化：仅在该主题还没有站点工作区时导入并发布，
 * 已编辑或已重置的站点一律不动，可重复执行。内容定制能力未装配或未启用时跳过，失败不阻断启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudioBuiltinSiteInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final ObjectProvider<StudioAppService> studioAppService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        StudioAppService studio = studioAppService.getIfAvailable();
        if (studio == null) {
            return;
        }
        try {
            if (studio.seedBuiltinSite()) {
                log.info("Built-in site seeded for theme {}", StudioAppService.BUILTIN_THEME_CODE);
            }
        }
        catch (RuntimeException e) {
            log.warn("Built-in site seeding skipped: {}", e.getMessage());
        }
    }
}
