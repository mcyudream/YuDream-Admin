package online.yudream.base.application.platform.capability.assembler;

import online.yudream.base.application.platform.capability.dto.CapabilityDTO;
import online.yudream.base.application.platform.capability.dto.CapabilityTestDTO;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;

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
                .config(module.getConfig())
                .status(health.status())
                .healthMessage(health.message())
                .checkedAt(health.checkedAt())
                .metrics(health.metrics())
                .build();
    }

    public static CapabilityTestDTO toDTO(CapabilityTestResult result) {
        return CapabilityTestDTO.builder()
                .success(result.success())
                .message(result.message())
                .testedAt(result.testedAt())
                .build();
    }
}
