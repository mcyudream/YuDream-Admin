package online.yudream.base.infra.system.setting.bootstrap;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.infra.system.integration.SystemIntegrationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 启动时装载环境派生设置。
 *
 * <p>插件经 {@code frameworkServices.setting(key)} 只能读取 sysSetting
 * 仓库（DefaultFrameworkServices 刻意不回退 Spring Environment，防止插件
 * 读取宿主任意属性与机密），而站点地址这类派生值的真实来源是
 * .env / 环境变量。不装载时，插件（ymcl-adapter、authlib-injector、
 * alipay 等）在 TLS 卸载且代理未透传 X-Forwarded-Proto 的部署下会把站点
 * 地址推导成 http，导致启动器侧凭据请求被 https-only 策略拒绝。
 *
 * <p>启动时按有效值 upsert 以下键，环境变化后随重启自动更新：
 *
 * <ul>
 *   <li>{@code app.web-url} —— 站点对外地址有效值</li>
 *   <li>{@code APP_WEB_URL} —— 同上的启动器兼容别名（ymcl-adapter ≤0.10.x 字面键）</li>
 *   <li>{@code app.base-url} —— 站点接口地址有效值</li>
 * </ul>
 *
 * <p>手工固定站点地址请配置 {@code site.web-url}（有效值解析中优先级最高），
 * 不要直接改这几个键——它们以环境为准，每次启动都会对齐。
 */
@Component
@RequiredArgsConstructor
public class SettingEnvProvisioner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(SettingEnvProvisioner.class);

    private final SettingRepo settingRepo;
    private final SystemIntegrationSettings integrationSettings;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String webUrl = integrationSettings.effectiveWebUrl();
        provision("app.web-url", webUrl, "站点对外地址（宿主启动时按环境自动同步，固定值请配置 site.web-url）");
        provision("APP_WEB_URL", webUrl, "站点对外地址（app.web-url 的启动器兼容别名，宿主启动时自动同步）");
        provision("app.base-url", integrationSettings.effectiveBaseUrl(), "站点接口地址（宿主启动时按环境自动同步）");
    }

    private void provision(String key, String derivedValue, String description) {
        try {
            Optional<Setting> existing = settingRepo.findByKey(key);
            if (existing.isEmpty()) {
                settingRepo.save(Setting.builder()
                        .key(key)
                        .value(derivedValue)
                        .type(SettingType.STRING)
                        .category("app")
                        .description(description)
                        .build());
                log.info("系统设置 {} 已按环境装载为 {}", key, derivedValue);
                return;
            }
            Setting setting = existing.get();
            if (derivedValue.equals(setting.getValue())) {
                return;
            }
            setting.setValue(derivedValue);
            settingRepo.save(setting);
            log.info("系统设置 {} 已按环境更新为 {}", key, derivedValue);
        } catch (Exception error) {
            log.warn("系统设置 {} 环境同步失败：{}", key, error.getMessage());
        }
    }
}
