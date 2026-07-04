package online.yudream.base.infra.platform.docs.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.domain.platform.docs.aggregate.ApiDocSettings;
import online.yudream.base.domain.platform.docs.repo.ApiDocSettingsRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.api-docs", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ApiDocCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "api-docs";

    private final ApiDocSettingsRepo apiDocSettingsRepo;

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "API 文档",
                CapabilityType.DOCUMENTATION,
                "提供 SpringDoc OpenAPI 接口文档和 Swagger UI 入口",
                "i-ri:file-list-2-line",
                80,
                Map.of("openApiPath", "/v3/api-docs", "swaggerUiPath", "/swagger-ui/index.html")
        );
    }

    @Override
    public CapabilityHealth health() {
        ApiDocSettings settings = currentSettings();
        if (!settings.isEnabled()) {
            return CapabilityHealth.disabled("API 文档未启用");
        }
        return CapabilityHealth.enabled("API 文档已启用", Map.of(
                "openApiPath", settings.getOpenApiPath(),
                "swaggerUiPath", settings.getSwaggerUiPath()
        ));
    }

    @Override
    public void enable(Map<String, String> config) {
        ApiDocSettings settings = currentSettings();
        settings.update(
                true,
                settings.isApiKeyAccessEnabled(),
                settings.getTitle(),
                settings.getDescription(),
                settings.getDocVersion(),
                config == null ? settings.getOpenApiPath() : config.getOrDefault("openApiPath", settings.getOpenApiPath()),
                config == null ? settings.getSwaggerUiPath() : config.getOrDefault("swaggerUiPath", settings.getSwaggerUiPath())
        );
        apiDocSettingsRepo.save(settings);
    }

    @Override
    public void disable() {
        ApiDocSettings settings = currentSettings();
        settings.update(false, settings.isApiKeyAccessEnabled(), settings.getTitle(), settings.getDescription(),
                settings.getDocVersion(), settings.getOpenApiPath(), settings.getSwaggerUiPath());
        apiDocSettingsRepo.save(settings);
    }

    @Override
    public CapabilityTestResult test(String message) {
        ApiDocSettings settings = currentSettings();
        if (!settings.isEnabled()) {
            return CapabilityTestResult.failure("API 文档未启用");
        }
        return CapabilityTestResult.success("API 文档入口：" + settings.getSwaggerUiPath());
    }

    private ApiDocSettings currentSettings() {
        return apiDocSettingsRepo.findDefault().orElseGet(() -> apiDocSettingsRepo.save(ApiDocSettings.createDefault()));
    }
}
