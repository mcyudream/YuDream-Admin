package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.HarnessRunReference;
import java.util.function.Consumer;

/** 应用层提供任务幂等键和私有寻址回调，基础设施在进入异步执行前捕获。 */
public final class HarnessExecutionScope implements AutoCloseable {
    private static final ThreadLocal<HarnessExecutionScope> CURRENT = new ThreadLocal<>();
    private final HarnessExecutionScope previous;
    private final String idempotencyKey;
    private final Consumer<HarnessRunReference> onReference;
    private final Consumer<online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile> onProfile;
    private final Consumer<online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot> onState;
    private volatile online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile resumeProfile;
    private boolean closed;

    private HarnessExecutionScope(String idempotencyKey, Consumer<HarnessRunReference> onReference,
                                  Consumer<online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile> onProfile,
                                  Consumer<online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot> onState) {
        this.previous = CURRENT.get();
        this.idempotencyKey = idempotencyKey;
        this.onReference = onReference;
        this.onProfile = onProfile;
        this.onState = onState;
        CURRENT.set(this);
    }

    public static HarnessExecutionScope open(String idempotencyKey, Consumer<HarnessRunReference> onReference) {
        return new HarnessExecutionScope(idempotencyKey, onReference, null, null);
    }
    public static HarnessExecutionScope open(String key, Consumer<HarnessRunReference> reference,
                                             Consumer<online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile> profile) {
        return new HarnessExecutionScope(key, reference, profile, null);
    }
    public static HarnessExecutionScope open(String key, Consumer<HarnessRunReference> reference,
                                             Consumer<online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile> profile,
                                             Consumer<online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot> state) {
        return new HarnessExecutionScope(key, reference, profile, state);
    }

    /** 工作流单次节点调用派生幂等键，继承私有引用、恢复配置与状态投影出口。 */
    public static HarnessExecutionScope child(String discriminator) {
        if (discriminator == null || discriminator.isBlank()) {
            throw new IllegalArgumentException("Agent 子调用标识不能为空");
        }
        HarnessExecutionScope parent = CURRENT.get();
        return parent == null ? new HarnessExecutionScope(null, null, null, null)
                : new HarnessExecutionScope(parent.idempotencyKey == null ? null
                        : parent.idempotencyKey + ":" + discriminator,
                        parent.onReference, parent.onProfile, parent.onState);
    }
    public static void reportState(online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot state) {
        HarnessExecutionScope scope = CURRENT.get();
        if (scope != null && scope.onState != null) scope.onState.accept(state);
    }
    public static void reportRequest(online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request) {
        HarnessExecutionScope scope = CURRENT.get();
        if (scope == null || scope.onProfile == null) return;
        java.util.List<AiAgentTool> scoped = AiAgentToolExecutionScope.currentTools();
        java.util.List<String> tools = !request.allowedToolNames().isEmpty() ? request.allowedToolNames()
                : scoped == null ? java.util.List.of() : scoped.stream().map(tool -> tool.descriptor().name()).toList();
        java.util.Map<String, String> config = request.config() == null ? java.util.Map.of() : request.config();
        String provider = request.providerCode() == null ? config.get("defaultProvider") : request.providerCode();
        String model = request.modelCode() == null ? config.get("defaultModel") : request.modelCode();
        scope.resumeProfile = new online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile(provider, model,
                request.systemPrompt(), request.toolCallingEnabled(), tools, false, request.userId(), true,
                AiInvocationContextScope.current());
        scope.onProfile.accept(scope.resumeProfile);
    }
    /** 捕获真实模型 schema 出口，回调可跨原生执行线程使用且不读取 ThreadLocal。 */
    public static Consumer<java.util.List<String>> captureAllowedToolsReporter() {
        HarnessExecutionScope scope = CURRENT.get();
        return scope == null || scope.onProfile == null ? null : scope::allowedTools;
    }
    public static void reportAllowedTools(java.util.List<String> names) {
        HarnessExecutionScope scope = CURRENT.get();
        if (scope != null) scope.allowedTools(names);
    }
    private synchronized void allowedTools(java.util.List<String> names) {
        if (resumeProfile == null || onProfile == null) return;
        java.util.List<String> exact = names == null ? java.util.List.of() : names.stream().distinct().sorted().toList();
        resumeProfile = new online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile(
                resumeProfile.providerCode(), resumeProfile.modelCode(), resumeProfile.systemPrompt(),
                resumeProfile.toolCallingEnabled(), exact, true, resumeProfile.nativeUserId(), resumeProfile.nativeActorCaptured(),
                resumeProfile.invocationContext());
        onProfile.accept(resumeProfile);
    }
    public static String currentIdempotencyKey() { return CURRENT.get() == null ? null : CURRENT.get().idempotencyKey; }
    public static Consumer<HarnessRunReference> currentReferenceConsumer() {
        return CURRENT.get() == null ? null : CURRENT.get().onReference;
    }
    @Override public void close() {
        if (closed) return;
        if (CURRENT.get() != this) throw new IllegalStateException("Agent 执行作用域必须按后进先出顺序关闭");
        closed = true;
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
