package online.yudream.base.interfaces.platform.milky.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record QqSandboxCreateRequest(
        String presetCode,
        @NotBlank String conversationType,
        @NotBlank String pluginCode,
        @NotBlank @Pattern(regexp = "[0-9]+") String policyConnectionId,
        String botId,
        @NotBlank String userId,
        String groupId,
        String nickname,
        String randomMode,
        Map<String, Object> metadata
) { }
