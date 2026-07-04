package online.yudream.base.application.platform.docs.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.docs.assembler.ApiDocAssembler;
import online.yudream.base.application.platform.docs.cmd.ApiDocSettingsUpdateCmd;
import online.yudream.base.application.platform.docs.dto.ApiDocSettingsDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.docs.aggregate.ApiDocSettings;
import online.yudream.base.domain.platform.docs.repo.ApiDocSettingsRepo;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApiDocAppService {

    private final ApiDocSettingsRepo apiDocSettingsRepo;
    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;

    @Transactional(readOnly = true)
    public ApiDocSettingsDTO settings() {
        return ApiDocAssembler.toDTO(currentSettings());
    }

    @Transactional
    public ApiDocSettingsDTO update(ApiDocSettingsUpdateCmd cmd) {
        ensureApiKeyAccessAvailable(cmd);
        ApiDocSettings settings = currentSettings();
        settings.update(
                cmd.isEnabled(),
                cmd.isApiKeyAccessEnabled(),
                cmd.getTitle(),
                cmd.getDescription(),
                cmd.getVersion(),
                cmd.getOpenApiPath(),
                cmd.getSwaggerUiPath()
        );
        return ApiDocAssembler.toDTO(apiDocSettingsRepo.save(settings));
    }

    @Transactional(readOnly = true)
    public boolean enabled() {
        return currentSettings().isEnabled();
    }

    private ApiDocSettings currentSettings() {
        return apiDocSettingsRepo.findDefault()
                .orElseGet(() -> apiDocSettingsRepo.save(ApiDocSettings.createDefault()));
    }

    private void ensureApiKeyAccessAvailable(ApiDocSettingsUpdateCmd cmd) {
        if (cmd.isApiKeyAccessEnabled() && !currentSecurityPolicy().isApiKeyEnabled()) {
            throw new BizException("请先启用系统 API Key，再允许 API 文档使用 API Key 访问");
        }
    }

    private ApiSecurityPolicy currentSecurityPolicy() {
        return apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
    }
}
