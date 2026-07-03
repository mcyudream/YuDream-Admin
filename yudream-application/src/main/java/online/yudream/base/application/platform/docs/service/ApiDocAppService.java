package online.yudream.base.application.platform.docs.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.docs.assembler.ApiDocAssembler;
import online.yudream.base.application.platform.docs.cmd.ApiDocSettingsUpdateCmd;
import online.yudream.base.application.platform.docs.dto.ApiDocSettingsDTO;
import online.yudream.base.domain.platform.docs.aggregate.ApiDocSettings;
import online.yudream.base.domain.platform.docs.repo.ApiDocSettingsRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApiDocAppService {

    private final ApiDocSettingsRepo apiDocSettingsRepo;

    @Transactional(readOnly = true)
    public ApiDocSettingsDTO settings() {
        return ApiDocAssembler.toDTO(currentSettings());
    }

    @Transactional
    public ApiDocSettingsDTO update(ApiDocSettingsUpdateCmd cmd) {
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
}
