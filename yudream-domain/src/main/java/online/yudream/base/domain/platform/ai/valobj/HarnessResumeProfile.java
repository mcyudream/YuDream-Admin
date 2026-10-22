package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;

/** 恢复时保持原模型与工具边界；不保存密钥、供应商配置或旧权限快照。 */
public record HarnessResumeProfile(String providerCode, String modelCode, String systemPrompt,
                                    boolean toolCallingEnabled, List<String> allowedToolNames,
                                    boolean exactToolScopeCaptured, Long nativeUserId, boolean nativeActorCaptured,
                                    AiInvocationContextSnapshot invocationContext) {
    public HarnessResumeProfile { allowedToolNames = allowedToolNames == null ? List.of() : List.copyOf(allowedToolNames); }

    /** 续跑尚未再次进入模型时，保留同一请求已经捕获的真实工具边界。 */
    public HarnessResumeProfile retainCapturedScope(HarnessResumeProfile previous) {
        if (exactToolScopeCaptured || previous == null || !previous.exactToolScopeCaptured
                || !java.util.Objects.equals(providerCode, previous.providerCode)
                || !java.util.Objects.equals(modelCode, previous.modelCode)
                || !java.util.Objects.equals(systemPrompt, previous.systemPrompt)
                || toolCallingEnabled != previous.toolCallingEnabled
                || !java.util.Objects.equals(nativeUserId, previous.nativeUserId)
                || nativeActorCaptured != previous.nativeActorCaptured
                || !java.util.Objects.equals(invocationContext, previous.invocationContext)
                || !new java.util.HashSet<>(allowedToolNames).equals(new java.util.HashSet<>(previous.allowedToolNames))) {
            return this;
        }
        return previous;
    }
    public HarnessResumeProfile(String providerCode, String modelCode, String systemPrompt,
                                boolean toolCallingEnabled, List<String> allowedToolNames) {
        this(providerCode, modelCode, systemPrompt, toolCallingEnabled, allowedToolNames, false, null, false, null);
    }
    public HarnessResumeProfile(String providerCode, String modelCode, String systemPrompt,
                                boolean toolCallingEnabled, List<String> allowedToolNames, boolean exactToolScopeCaptured) {
        this(providerCode, modelCode, systemPrompt, toolCallingEnabled, allowedToolNames, exactToolScopeCaptured, null, false, null);
    }
    public HarnessResumeProfile(String providerCode, String modelCode, String systemPrompt,
                                boolean toolCallingEnabled, List<String> allowedToolNames, boolean exactToolScopeCaptured,
                                Long nativeUserId, boolean nativeActorCaptured) {
        this(providerCode, modelCode, systemPrompt, toolCallingEnabled, allowedToolNames, exactToolScopeCaptured,
                nativeUserId, nativeActorCaptured, null);
    }
}
