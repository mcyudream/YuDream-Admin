package online.yudream.base.infra.platform.ai.service.provider;

import org.springframework.util.StringUtils;

public record AiModelEndpoint(
        String code,
        String name,
        String model,
        String temperature,
        String reasoningEffort,
        Boolean thinkingEnabled,
        String extraBody,
        String kind,
        boolean vision
) {
    public String optionCode() {
        return StringUtils.hasText(code) ? code.trim() : modelName();
    }

    public String displayName() {
        return StringUtils.hasText(name) ? name.trim() : modelName();
    }

    public String modelName() {
        return StringUtils.hasText(model) ? model.trim() : optionCodeFallback();
    }

    private String optionCodeFallback() {
        return StringUtils.hasText(code) ? code.trim() : "";
    }
}
