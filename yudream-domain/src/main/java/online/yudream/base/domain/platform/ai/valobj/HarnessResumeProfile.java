package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;

/** 恢复时保持原模型与工具边界；不保存密钥、供应商配置或旧权限快照。 */
public record HarnessResumeProfile(String providerCode, String modelCode, String systemPrompt,
                                    boolean toolCallingEnabled, List<String> allowedToolNames,
                                    boolean exactToolScopeCaptured, Long nativeUserId, boolean nativeActorCaptured,
                                    AiInvocationContextSnapshot invocationContext) {
    public HarnessResumeProfile { allowedToolNames = allowedToolNames == null ? List.of() : List.copyOf(allowedToolNames); }
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
