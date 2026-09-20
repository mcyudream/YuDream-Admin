package online.yudream.base.application.installer.event;

/**
 * 引导配置已落盘事件：bootstrap 层监听后延迟退出进程，由容器重启策略拉起进入正常模式。
 */
public record InstallerConfigAppliedEvent(String bootstrapFileLocation) {
}
