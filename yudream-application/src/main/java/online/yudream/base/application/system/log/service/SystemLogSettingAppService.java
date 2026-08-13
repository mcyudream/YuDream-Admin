package online.yudream.base.application.system.log.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.log.cmd.DockerLogSettingsUpdateCmd;
import online.yudream.base.application.system.log.dto.DockerLogSettingsDTO;
import online.yudream.base.application.system.log.event.DockerLogSettingsChanged;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Docker 容器日志采集配置的读写应用服务，配置持久化到系统设置（JSON），并发布变更事件驱动采集源重载。
 */
@Service
@RequiredArgsConstructor
public class SystemLogSettingAppService {

    private static final String SETTING_KEY = "system.log.docker";
    private static final String CATEGORY = "system-log";

    private final SettingRepo settingRepo;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${yudream.system.log.docker.enabled:false}")
    private boolean enabledDefault;
    @Value("${yudream.system.log.docker.containers:}")
    private String containersDefault;
    @Value("${yudream.system.log.docker.transport:auto}")
    private String transportDefault;
    @Value("${yudream.system.log.docker.socket:/var/run/docker.sock}")
    private String socketDefault;
    @Value("${yudream.system.log.docker.tail:200}")
    private long tailDefault;

    @Transactional(readOnly = true)
    public DockerLogSettingsDTO dockerSettings() {
        return settingRepo.findByKey(SETTING_KEY)
                .map(Setting::getValue)
                .filter(StringUtils::hasText)
                .map(this::parse)
                .orElseGet(this::defaults);
    }

    @Transactional
    public DockerLogSettingsDTO update(DockerLogSettingsUpdateCmd cmd) {
        DockerLogSettingsDTO dto = new DockerLogSettingsDTO(
                cmd.enabled(), cmd.containers(), cmd.transport(), cmd.socket(), cmd.tail());
        if (dto.enabled() && dto.containers().isEmpty()) {
            throw new BizException("启用容器日志采集时至少需要配置一个容器名称");
        }
        save(dto);
        eventPublisher.publishEvent(new DockerLogSettingsChanged(dto));
        return dto;
    }

    private DockerLogSettingsDTO defaults() {
        return DockerLogSettingsDTO.defaults(enabledDefault, containersDefault, transportDefault, socketDefault, tailDefault);
    }

    private DockerLogSettingsDTO parse(String json) {
        try {
            return objectMapper.readValue(json, DockerLogSettingsDTO.class);
        } catch (Exception error) {
            throw new BizException("容器日志配置解析失败");
        }
    }

    private void save(DockerLogSettingsDTO dto) {
        String json;
        try {
            json = objectMapper.writeValueAsString(dto);
        } catch (Exception error) {
            throw new BizException("容器日志配置保存失败");
        }
        Setting setting = settingRepo.findByKey(SETTING_KEY).orElseGet(() -> Setting.builder()
                .key(SETTING_KEY)
                .type(SettingType.JSON)
                .category(CATEGORY)
                .description("Docker 容器日志采集")
                .build());
        setting.setValue(json);
        setting.setType(SettingType.JSON);
        setting.setCategory(CATEGORY);
        setting.setDescription("Docker 容器日志采集");
        settingRepo.save(setting);
    }
}
