package online.yudream.base.interfaces.platform.milky.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QqSandboxCaseSaveRequest(
        String id,
        @NotBlank(message = "用例名称不能为空") String name,
        String description,
        @NotNull(message = "会话初始参数不能为空") @Valid QqSandboxCaseSetupRequest setup,
        @NotEmpty(message = "用例至少包含一条消息步骤") @Valid List<QqSandboxCaseStepRequest> steps
) {
    public record QqSandboxCaseSetupRequest(
            String pluginCode,
            @NotBlank(message = "策略连接不能为空") String policyConnectionId,
            String selfId,
            @NotBlank(message = "发送人 QQ 不能为空") String userId,
            String nickname,
            @NotBlank(message = "会话渠道不能为空") String channelId,
            @NotBlank(message = "会话场景不能为空") String scene,
            String randomMode,
            Boolean forceUnbound,
            List<String> simulateRoles
    ) { }

    // content 是否必填取决于 type（message 必填、button 用 buttonId、group_request 可空），由应用层按类型校验
    public record QqSandboxCaseStepRequest(
            String senderId,
            String nickname,
            String content,
            boolean mentionSelf,
            List<String> mentions,
            String replyMessageId,
            String type,
            String buttonId
    ) { }
}
