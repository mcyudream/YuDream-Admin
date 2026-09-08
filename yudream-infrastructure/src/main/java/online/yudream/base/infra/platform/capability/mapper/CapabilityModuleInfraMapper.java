package online.yudream.base.infra.platform.capability.mapper;

import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.valobj.CapabilitySecrets;
import online.yudream.base.infra.platform.capability.dataobj.CapabilityModuleDO;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class CapabilityModuleInfraMapper {

    public static CapabilityModuleDO toDataObj(CapabilityModule module, CapabilityCredentialCipher credentialCipher) {
        if (module == null) {
            return null;
        }
        CapabilityModuleDO dataObj = new CapabilityModuleDO();
        dataObj.setId(module.getId());
        dataObj.setCode(module.getCode());
        dataObj.setName(module.getName());
        dataObj.setType(module.getType());
        dataObj.setDescription(module.getDescription());
        dataObj.setIcon(module.getIcon());
        dataObj.setSort(module.getSort());
        dataObj.setEnabled(module.getEnabled());
        dataObj.setConfig(toStoredConfig(module, credentialCipher));
        dataObj.setDependencies(module.getDependencies());
        dataObj.setVersion(module.getVersion());
        dataObj.setCreateTime(module.getCreateTime());
        dataObj.setUpdateTime(module.getUpdateTime());
        return dataObj;
    }

    public static CapabilityModule toDomain(CapabilityModuleDO dataObj, CapabilityCredentialCipher credentialCipher) {
        if (dataObj == null) {
            return null;
        }
        return CapabilityModule.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .name(dataObj.getName())
                .type(dataObj.getType())
                .description(dataObj.getDescription())
                .icon(dataObj.getIcon())
                .sort(dataObj.getSort())
                .enabled(dataObj.getEnabled())
                .config(toRuntimeConfig(dataObj, credentialCipher))
                .dependencies(dataObj.getDependencies())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    private static Map<String, String> toStoredConfig(CapabilityModule module, CapabilityCredentialCipher credentialCipher) {
        Map<String, String> config = new HashMap<>(module.getConfig() == null ? Map.of() : module.getConfig());
        for (String secretKey : CapabilitySecrets.keysOf(module.getCode())) {
            String value = config.get(secretKey);
            if (StringUtils.hasText(value) && !credentialCipher.encrypted(value) && credentialCipher.canEncrypt()) {
                config.put(secretKey, credentialCipher.encryptSecret(module.getCode(), secretKey, value));
            }
        }
        return config;
    }

    private static Map<String, String> toRuntimeConfig(CapabilityModuleDO dataObj, CapabilityCredentialCipher credentialCipher) {
        Map<String, String> config = new HashMap<>(dataObj.getConfig() == null ? Map.of() : dataObj.getConfig());
        for (String secretKey : CapabilitySecrets.keysOf(dataObj.getCode())) {
            if (!config.containsKey(secretKey)) {
                continue;
            }
            try {
                config.put(secretKey, credentialCipher.decryptSecret(dataObj.getCode(), secretKey, config.get(secretKey)));
            } catch (RuntimeException ignored) {
                // Keep historical ciphertext untouched so descriptor synchronization can recover after a key is configured.
            }
        }
        return config;
    }
}
