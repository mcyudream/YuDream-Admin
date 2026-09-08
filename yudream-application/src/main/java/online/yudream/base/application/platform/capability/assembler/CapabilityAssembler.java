package online.yudream.base.application.platform.capability.assembler;

import online.yudream.base.application.platform.capability.dto.CapabilityDTO;
import online.yudream.base.application.platform.capability.dto.CapabilityTestDTO;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilitySecrets;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;

import java.util.HashMap;
import java.util.Map;

public class CapabilityAssembler {

    public static CapabilityDTO toDTO(CapabilityModule module, CapabilityHealth health) {
        return CapabilityDTO.builder()
                .code(module.getCode())
                .name(module.getName())
                .type(module.getType())
                .description(module.getDescription())
                .icon(module.getIcon())
                .sort(module.getSort())
                .enabled(module.getEnabled())
                .dependencies(module.getDependencies())
                .config(publicConfig(module))
                .secretConfigured(secretConfigured(module))
                .status(health.status())
                .healthMessage(health.message())
                .checkedAt(health.checkedAt())
                .metrics(health.metrics())
                .build();
    }

    private static Map<String, String> publicConfig(CapabilityModule module) {
        Map<String, String> config = new HashMap<>(module.getConfig() == null ? Map.of() : module.getConfig());
        CapabilitySecrets.keysOf(module.getCode()).forEach(config::remove);
        return config;
    }

    private static Map<String, Boolean> secretConfigured(CapabilityModule module) {
        Map<String, Boolean> result = new HashMap<>();
        for (String key : CapabilitySecrets.keysOf(module.getCode())) {
            String value = module.getConfig() == null ? null : module.getConfig().get(key);
            result.put(key, value != null && !value.isBlank());
        }
        return result;
    }

    public static CapabilityTestDTO toDTO(CapabilityTestResult result) {
        return CapabilityTestDTO.builder()
                .success(result.success())
                .message(result.message())
                .testedAt(result.testedAt())
                .build();
    }
}
