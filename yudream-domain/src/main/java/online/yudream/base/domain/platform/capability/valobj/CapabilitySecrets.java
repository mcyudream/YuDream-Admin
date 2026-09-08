package online.yudream.base.domain.platform.capability.valobj;

import java.util.Map;
import java.util.Set;

/**
 * 能力配置中的加密键注册表：列出的配置键在入库时经凭据主密钥加密，
 * 对外 DTO 不返回值、只回报 secretConfigured 标记。
 */
public final class CapabilitySecrets {

    private static final Map<String, Set<String>> SECRET_KEYS = Map.of(
            "neo4j", Set.of("password"),
            "inbound-mail", Set.of("password")
    );

    private CapabilitySecrets() {
    }

    public static Set<String> keysOf(String capabilityCode) {
        return SECRET_KEYS.getOrDefault(capabilityCode, Set.of());
    }

    public static boolean isSecret(String capabilityCode, String configKey) {
        return keysOf(capabilityCode).contains(configKey);
    }
}
