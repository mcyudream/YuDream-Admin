package online.yudream.base.application.platform.capability.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.assembler.CapabilityAssembler;
import online.yudream.base.application.platform.capability.cmd.CapabilityConfigUpdateCmd;
import online.yudream.base.application.platform.capability.cmd.CapabilityTestCmd;
import online.yudream.base.application.platform.capability.dto.CapabilityDTO;
import online.yudream.base.application.platform.capability.dto.CapabilityTestDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CapabilityAppService {

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final List<CapabilityProvider> providers;

    @Transactional
    public List<CapabilityDTO> list() {
        syncDescriptors();
        return capabilityModuleRepo.findAll().stream()
                .sorted(Comparator.comparing(CapabilityModule::getSort, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(module -> CapabilityAssembler.toDTO(module, healthOf(module)))
                .toList();
    }

    @Transactional
    public CapabilityDTO updateConfig(CapabilityConfigUpdateCmd cmd) {
        CapabilityModule module = module(cmd.getCode());
        module.updateConfig(cmd.getConfig());
        CapabilityModule saved = capabilityModuleRepo.save(module);
        if (saved.enabled()) {
            provider(saved.getCode()).enable(saved.getConfig());
        }
        return CapabilityAssembler.toDTO(saved, healthOf(saved));
    }

    @Transactional
    public CapabilityDTO enable(String code) {
        CapabilityModule module = module(code);
        module.enable();
        CapabilityModule saved = capabilityModuleRepo.save(module);
        provider(code).enable(saved.getConfig());
        return CapabilityAssembler.toDTO(saved, healthOf(saved));
    }

    @Transactional
    public CapabilityDTO disable(String code) {
        CapabilityModule module = module(code);
        module.disable();
        CapabilityModule saved = capabilityModuleRepo.save(module);
        provider(code).disable();
        return CapabilityAssembler.toDTO(saved, healthOf(saved));
    }

    @Transactional(readOnly = true)
    public CapabilityTestDTO test(CapabilityTestCmd cmd) {
        CapabilityModule module = module(cmd.getCode());
        if (!module.enabled()) {
            throw new BizException("能力未启用");
        }
        String message = StringUtils.hasText(cmd.getMessage()) ? cmd.getMessage() : "YuDream 能力测试消息";
        return CapabilityAssembler.toDTO(provider(cmd.getCode()).test(message));
    }

    public void syncDescriptors() {
        Map<String, CapabilityProvider> providerMap = providerMap();
        for (CapabilityProvider provider : providerMap.values()) {
            CapabilityDescriptor descriptor = provider.descriptor();
            CapabilityModule module = capabilityModuleRepo.findByCode(descriptor.code())
                    .orElseGet(() -> CapabilityModule.fromDescriptor(descriptor));
            module.refreshDescriptor(descriptor);
            capabilityModuleRepo.save(module);
        }
    }

    public void restoreEnabledProviders() {
        syncDescriptors();
        for (CapabilityModule module : capabilityModuleRepo.findAll()) {
            if (module.enabled()) {
                provider(module.getCode()).enable(module.getConfig());
            }
        }
    }

    private CapabilityModule module(String code) {
        return capabilityModuleRepo.findByCode(code)
                .orElseThrow(() -> new BizException("能力不存在"));
    }

    private CapabilityProvider provider(String code) {
        CapabilityProvider provider = providerMap().get(code);
        if (provider == null) {
            throw new BizException("能力提供器不存在");
        }
        return provider;
    }

    private CapabilityHealth healthOf(CapabilityModule module) {
        if (!module.enabled()) {
            return CapabilityHealth.disabled("能力未启用");
        }
        return provider(module.getCode()).health();
    }

    private Map<String, CapabilityProvider> providerMap() {
        return providers.stream()
                .collect(Collectors.toMap(provider -> provider.descriptor().code(), Function.identity(), (a, b) -> a));
    }
}
