package online.yudream.base.infra.platform.ai.service.provider;

import org.springframework.util.StringUtils;

public enum AiProviderType {
    OPENAI,
    OPENAI_COMPATIBLE,
    KIMI,
    DEEPSEEK;

    public static AiProviderType from(String value) {
        if (!StringUtils.hasText(value)) {
            return OPENAI_COMPATIBLE;
        }
        try {
            return AiProviderType.valueOf(value.trim().replace('-', '_').toUpperCase());
        } catch (IllegalArgumentException e) {
            return OPENAI_COMPATIBLE;
        }
    }
}
