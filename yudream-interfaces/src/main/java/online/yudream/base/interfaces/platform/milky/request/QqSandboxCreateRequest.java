package online.yudream.base.interfaces.platform.milky.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

public record QqSandboxCreateRequest(
        String presetCode,
        @NotBlank String conversationType,
        // 留空表示不限定插件，沙盒消息广播给全部已启用插件，与真实 QQ 群一致
        String pluginCode,
        @NotBlank @Pattern(regexp = "[0-9]+") String policyConnectionId,
        String botId,
        @NotBlank String userId,
        String groupId,
        String nickname,
        String randomMode,
        // 身份模拟：forceUnbound 强制未绑定；simulateRoles 缺省走真实角色、空数组表示无角色
        Boolean forceUnbound,
        List<String> simulateRoles,
        Map<String, Object> metadata
) { }
