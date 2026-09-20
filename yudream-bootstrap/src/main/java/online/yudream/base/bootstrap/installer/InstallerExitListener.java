package online.yudream.base.bootstrap.installer;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.installer.event.InstallerConfigAppliedEvent;
import online.yudream.base.application.installer.service.InstallerAppService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 安装完成退出监听：引导配置落盘后延迟退出进程，
 * Docker（restart: unless-stopped）会自动拉起容器进入正常模式。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "yudream.bootstrap.installer", havingValue = "true")
public class InstallerExitListener {

    @EventListener(InstallerConfigAppliedEvent.class)
    public void onConfigApplied(InstallerConfigAppliedEvent event) {
        Thread terminator = new Thread(() -> {
            try {
                Thread.sleep(InstallerAppService.RESTART_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.warn("安装配置已写入 {}，进程即将退出并由容器重启策略拉起进入正常模式",
                    event.bootstrapFileLocation());
            System.exit(0);
        }, "installer-exit");
        terminator.setDaemon(true);
        terminator.start();
    }
}
