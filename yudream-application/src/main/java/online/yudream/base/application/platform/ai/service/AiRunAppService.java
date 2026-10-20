package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.ai.assembler.AiRunAssembler;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import online.yudream.base.domain.platform.ai.enumerate.AiRunStatus;
import online.yudream.base.domain.platform.ai.repo.AiRunRepo;
import online.yudream.base.domain.platform.ai.valobj.AiRunEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 持久的 AI 界面事件出口。这里仅托管业务调用与投影，AgentScope AgentSession
 * 负责推理、队列、上下文、工具循环与检查点。订阅者退出不会结束业务运行。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiRunAppService {
    private final AiRunRepo repo;
    private final CapabilityAppService capabilities;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final online.yudream.base.domain.platform.ai.service.HarnessAgentGateway harness;
    private final online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo capabilityRepo;
    private final online.yudream.base.domain.platform.ai.service.AiUsageQuotaGateway usageQuota;
    private final List<AiRunResultProjector> projectors;
    private List<AiRunContinuation> continuations = List.of();
    private online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway invocationContexts;
    private online.yudream.base.domain.platform.ai.service.AiActorPermissionGateway actorPermissions;
    private CmsStudioClientTools studioTools;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void studioTools(CmsStudioClientTools tools) { this.studioTools = tools; }
    private final String instanceId = UUID.randomUUID().toString();
    private final Map<String, RunEmitter> active = new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void continuations(List<AiRunContinuation> values) { this.continuations = List.copyOf(values); }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void invocationContexts(online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway gateway) { this.invocationContexts = gateway; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void actorPermissions(online.yudream.base.domain.platform.ai.service.AiActorPermissionGateway gateway) { this.actorPermissions = gateway; }

    public AiRunDTO start(Long userId, String module, String sessionId,
                          Function<RunEmitter, Object> action, Runnable onCancel) {
        ensureEnabled();
        // 同会话遗留的僵尸运行先按陈旧规则回收（服务中断/进程被杀的 RUNNING 占位），
        // 否则唯一索引会一直阻塞该会话的新运行，直到有人先读取过那条运行。
        for (AiRun stale : repo.list(userId, sessionId, true)) {
            recoverStale(stale);
        }
        AiRun run = repo.create(AiRun.start(userId, sessionId, module, instanceId));
        RunEmitter emitter = new RunEmitter(run, onCancel);
        return launch(run, emitter, action);
    }

    /** 既有串行队列在当前工作线程上写同一运行投影，不引入第二个任务调度器。 */
    public <T> T execute(Long userId, String module, String sessionId,
                         Function<RunEmitter, T> action, Runnable onCancel) {
        ensureEnabled();
        AiRun run = repo.create(AiRun.start(userId, sessionId, module, instanceId));
        RunEmitter emitter = new RunEmitter(run, onCancel);
        emitter.worker = Thread.currentThread();
        active.put(run.getId(), emitter);
        emitter.progress("accepted", Map.of("title", "请求已接收", "status", "running"));
        Thread.startVirtualThread(() -> heartbeat(emitter));
        try (var usage = online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope.open(run.getUserId());
             var scope = online.yudream.base.domain.platform.ai.service.HarnessExecutionScope.open(run.getId(), emitter::privateReference, emitter::privateProfile, emitter::privateState);
             var observation = online.yudream.base.domain.platform.ai.service.AiGenerationObserverScope.open(emitter.generationObserver())) {
            T result = action.apply(emitter);
            emitter.complete(result);
            return result;
        } catch (RuntimeException error) {
            if (!emitter.terminal) emitter.fail(error instanceof BizException ? error.getMessage() : "AI 运行失败，请稍后重试");
            throw error;
        } catch (Throwable error) {
            // Error（如栈溢出、内存不足）同样要落终态，否则运行会停留在 RUNNING 直到超时回收。
            if (!emitter.terminal) emitter.fail("AI 运行失败，请稍后重试");
            throw error;
        } finally { active.remove(run.getId()); }
    }

    private AiRunDTO launch(AiRun run, RunEmitter emitter, Function<RunEmitter, Object> action) {
        active.put(run.getId(), emitter);
        emitter.progress("accepted", Map.of("title", "请求已接收", "status", "running"));
        Thread worker = Thread.ofVirtual().unstarted(() -> {
            try {
                emitter.checkActive();
                Object result;
                try (var usage = online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope.open(run.getUserId());
                     var scope = online.yudream.base.domain.platform.ai.service.HarnessExecutionScope.open(run.getId(), emitter::privateReference, emitter::privateProfile, emitter::privateState);
                     var observation = online.yudream.base.domain.platform.ai.service.AiGenerationObserverScope.open(emitter.generationObserver())) {
                    result = action.apply(emitter);
                }
                emitter.complete(result);
            } catch (Throwable error) {
                if (!emitter.terminal) {
                    log.warn("AI 运行失败，runId={}", run.getId(), error);
                    emitter.fail(error instanceof BizException ? error.getMessage() : "AI 运行失败，请稍后重试");
                }
                if (error instanceof Error runtimeError) throw runtimeError;
                if (!(error instanceof RuntimeException runtimeException)) throw new IllegalStateException(error);
                throw runtimeException;
            } finally {
                active.remove(run.getId());
            }
        });
        emitter.worker = worker;
        Thread.startVirtualThread(() -> heartbeat(emitter));
        worker.start();
        return get(run.getUserId(), run.getId());
    }

    /** 用户明确请求时才接续框架检查点；不会再次 submit 同一条输入。 */
    public AiRunDTO resume(Long userId, String id, List<String> permissionCodes) {
        AiRun run = owned(userId, id);
        recoverStale(run);
        if (!run.isNativeResumeSupported() || run.getNativeReference() == null || run.getNativeProfile() == null || run.getStatus() != AiRunStatus.FAILED) {
            throw new BizException("此运行没有可接续的原生会话检查点，请继续会话");
        }
        var reference = run.getNativeReference();
        var profile = run.getNativeProfile();
        if (profile.allowedToolNames().stream().anyMatch(name -> name.startsWith("ui.") || name.startsWith("ui_"))) {
            throw new BizException("此检查点包含已过期的页面工具授权，请重新开启助手并继续会话");
        }
        var currentPermissions = executionPermissions(run, permissionCodes);
        var request = requestFor(run, currentPermissions);
        var nativeState = harness.inspect(request);
        if (!nativeState.uncertainToolCallIds().isEmpty() || !nativeState.pendingConfirmations().isEmpty()) {
            throw new BizException("原生会话存在待确认或执行结果不确定的工具，请先在会话中处理确认");
        }
        if (!repo.reopen(id, instanceId)) throw new BizException("运行状态已变化，请刷新后重试");
        run.setStatus(AiRunStatus.RUNNING); run.setError(null); run.setCancelRequested(false);
        RunEmitter emitter = new RunEmitter(run, () -> harness.interrupt(nativeActor(run), reference.sessionId(), reference.agentCode()));
        return launch(run, emitter, output -> {
            try (var invocation = restoreInvocation(run, currentPermissions)) {
            var tools = restoredTools(run, output, currentPermissions);
            online.yudream.base.domain.platform.ai.valobj.HarnessRunResult result;
            if (tools == null) result = harness.resume(request, reference.turnId(), output::content, output::reasoning, output::tool,
                    progress -> output.progress(progress.action(), progress));
            else try (var scope = online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope.open(tools, run.getUserId())) {
                result = harness.resume(request, reference.turnId(), output::content, output::reasoning, output::tool,
                        progress -> output.progress(progress.action(), progress));
            }
            return continueBusiness(run, output, result, currentPermissions);
            }
        });
    }

    public AiRunDTO get(Long userId, String id) {
        AiRun run = owned(userId, id);
        recoverStale(run);
        return snapshot(run);
    }
    public AiRunDTO getInvocationRun(Long owner, String id, online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot expected) {
        AiRun run = owned(owner, id);
        if (java.util.Objects.equals(owner, 0L)) {
            if (!java.util.Set.of("plugin-ai", "plugin-agent").contains(run.getModule()) || expected == null)
                throw new BizException("此系统运行不属于当前插件调用");
            if (java.util.stream.Stream.of(expected.platformUserId(), expected.connectionId(), expected.channelId(), expected.traceId())
                    .allMatch(value -> value == null || value.isBlank())) throw new BizException("系统插件调用必须提供稳定来源寻址");
            var original = run.getInvocationContext() != null ? run.getInvocationContext()
                    : run.getNativeProfile() == null ? null : run.getNativeProfile().invocationContext();
            if (original == null || !java.util.Objects.equals(original.platformUserId(), expected.platformUserId())
                    || !java.util.Objects.equals(original.connectionId(), expected.connectionId())
                    || !java.util.Objects.equals(original.channelId(), expected.channelId())
                    || !java.util.Objects.equals(original.traceId(), expected.traceId())) throw new BizException("无权观察其他来源的系统 AI 运行");
        }
        recoverStale(run); return snapshot(run);
    }

    public List<AiRunDTO> list(Long userId, String sessionId, boolean activeOnly) {
        ensureEnabled();
        if (userId == null) throw new BizException("当前用户未登录");
        return repo.list(userId, sessionId, activeOnly).stream().map(run -> {
            recoverStale(run);
            return snapshot(run);
        }).filter(dto -> !activeOnly || !dto.status().terminal()).toList();
    }

    public List<AiRunEvent> events(Long userId, String id, long after) {
        owned(userId, id);
        if (after < 0) throw new BizException("事件游标不能小于 0");
        return repo.events(id, after, 256);
    }

    public boolean finished(Long userId, String id, long cursor) {
        AiRun run = owned(userId, id);
        return run.getStatus().terminal() && cursor >= run.getCursor();
    }

    public AiRunDTO cancel(Long userId, String id) {
        AiRun run = owned(userId, id);
        recoverStale(run);
        if (!run.getStatus().terminal()) {
            if (run.getStatus() == AiRunStatus.WAITING) {
                // 同回应共用状态 CAS，取消不能与另一实例恢复原生任务并发写日志。
                if (repo.continueWaiting(id, instanceId)) {
                    try { new RunEmitter(run, null).cancel(); }
                    catch (RuntimeException error) { repo.waitForInput(id, run.getResult()); throw error; }
                }
            }
            else {
                RunEmitter local = active.get(id);
                if (local != null) local.cancel();
                // 由拥有模型运行的实例产生终态事件，避免并发写相同事件游标。
                else repo.requestCancel(id);
            }
        }
        return get(userId, id);
    }

    public void frontendResult(Long userId, String id, String toolCallId, boolean ok,
                               Map<String, Object> result, String error) {
        frontendResult(userId, id, toolCallId, ok, result, error, List.of());
    }

    public void frontendResult(Long userId, String id, String toolCallId, boolean ok,
                               Map<String, Object> result, String error, List<String> permissions) {
        AiRun run = owned(userId, id);
        recoverStale(run);
        if (run.getStatus().terminal()) throw new BizException("运行已结束，不能再提交工具结果");
        // prepare 已公布请求而原生日志尚在提交时，不核销回执，避免把结果卡在落盘窗口中。
        if (run.getNativeReference() != null && run.getNativeProfile() != null) {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (run.getStatus() == AiRunStatus.RUNNING && System.nanoTime() < deadline) {
                try { Thread.sleep(25); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new BizException("前端工具回执等待已中断");
                }
                run = owned(userId, id);
            }
            if (run.getStatus() == AiRunStatus.RUNNING) throw new BizException("原生交互仍在保存，请稍后重试");
            if (run.getStatus().terminal()) throw new BizException("运行已结束，不能再提交工具结果");
        }
        if (run.getNativeReference() != null && "cms-studio".equals(run.getNativeReference().agentCode())) {
            // Recheck before persisting the receipt: revoked permissions must not consume a pending tool.
            requestFor(run, executionPermissions(run, permissions));
        }
        var response = frontendResponse(ok, result, error);
        if (!repo.acknowledgeFrontend(id, toolCallId, response)) {
            // 回执已提交而进程在 native.respond 前退出：只消费原回执，不能替换或重复执行前端操作。
            if (run.getStatus() == AiRunStatus.WAITING && run.getNativeSnapshot() != null)
                response = repo.frontendResponse(id, toolCallId).orElseThrow(() -> new BizException("前端工具请求不存在、已处理或已过期"));
            else throw new BizException("前端工具请求不存在、已处理或已过期");
        }
        if (run.getStatus() == AiRunStatus.WAITING && run.getNativeSnapshot() != null) {
            respond(userId, id, toolCallId, null, response, permissions);
        }
    }

    public AiRunDTO respond(Long userId, String id, String requestId, Boolean approved,
                             Map<String, Object> output, List<String> permissions) {
        AiRun run = owned(userId, id);
        if (run.getStatus() != AiRunStatus.WAITING || run.getNativeReference() == null || run.getNativeProfile() == null
                || run.getNativeSnapshot() == null) throw new BizException("此运行没有待处理的原生交互");
        var interaction = run.getNativeSnapshot().pendingInteractions().stream().filter(item -> item.requestId().equals(requestId))
                .findFirst().orElseThrow(() -> new BizException("原生交互不存在或已处理"));
        if ("confirmation".equals(interaction.kind()) && approved == null) throw new BizException("请明确批准或拒绝工具执行");
        if ("external_execution".equals(interaction.kind()) && output == null) throw new BizException("外部工具必须提交真实执行结果");
        var currentPermissions = executionPermissions(run, permissions);
        var request = requestFor(run, currentPermissions);
        RunEmitter emitter = new RunEmitter(run, () -> harness.interrupt(nativeActor(run), run.getNativeReference().sessionId(), run.getNativeReference().agentCode()));
        // 工作流归属、拓扑和原工具回调的复核先于状态转换；拒绝后仍可取消真实挂起任务。
        List<online.yudream.base.domain.platform.ai.service.AiAgentTool> tools;
        try (var invocation = restoreInvocation(run, currentPermissions)) { tools = restoredTools(run, emitter, currentPermissions); }
        if (!repo.continueWaiting(id, instanceId)) throw new BizException("运行正在处理其他回执，请稍后刷新");
        run.setStatus(AiRunStatus.RUNNING);
        return launch(run, emitter, responseEmitter -> {
            try (var invocation = restoreInvocation(run, currentPermissions)) {
            if ("external_execution".equals(interaction.kind())) {
                String logicalName = logicalToolName(run, requestId, interaction.toolName());
                responseEmitter.event("ai.frontend", "settled", Map.of("toolCallId", requestId, "response", output));
                responseEmitter.event("ai.tool", "result", Map.of("tool", Map.of("toolCallId", requestId,
                        "toolName", logicalName, "status", Boolean.FALSE.equals(output.get("ok")) ? "error" : "complete", "payload", output)));
            }
            {
                var result = respondNative(run, request, requestId, approved, output, responseEmitter, tools);
                return continueBusiness(run, responseEmitter, result, currentPermissions);
            }
            }
        });
    }

    private Object continueBusiness(AiRun run, RunEmitter emitter, online.yudream.base.domain.platform.ai.valobj.HarnessRunResult result,
                                    List<String> permissions) {
        for (var continuation : continuations) {
            var value = continuation.continueAfterNative(run, result, java.util.Set.copyOf(permissions), emitter);
            if (value.isPresent()) {
                if (!"chat".equals(run.getModule())) return value.get();
                // 工作流恢复沿用原助手消息；业务 end 节点正文与累计 usage 是最终权威结果。
                Map<String, Object> merged = run.getResult() == null ? new LinkedHashMap<>()
                        : json.convertValue(run.getResult(), new com.fasterxml.jackson.core.type.TypeReference<>() { });
                Map<String, Object> business = json.convertValue(value.get(), new com.fasterxml.jackson.core.type.TypeReference<>() { });
                for (String field : List.of("content", "reasoning", "usage")) if (business.containsKey(field)) merged.put(field, business.get(field));
                var prefix = snapshot(repo.find(run.getId()).orElseThrow());
                merged.put("tools", prefix.tools()); merged.put("activities", prefix.activities());
                return merged;
            }
        }
        return continuedResult(run, emitter, result);
    }

    private Map<String, Object> continuedResult(AiRun original, RunEmitter emitter,
                                                online.yudream.base.domain.platform.ai.valobj.HarnessRunResult result) {
        Map<String, Object> value = original.getResult() == null ? new LinkedHashMap<>()
                : json.convertValue(original.getResult(), new com.fasterxml.jackson.core.type.TypeReference<>() { });
        var prefix = snapshot(repo.find(original.getId()).orElseThrow());
        value.put("sessionId", original.getSessionId()); value.put("content", emitter.run.getContent()); value.put("reasoning", emitter.run.getReasoning());
        value.put("tools", prefix.tools()); value.put("activities", prefix.activities());
        var previousUsage = value.get("usage") == null ? online.yudream.base.domain.platform.ai.valobj.AiUsage.empty()
                : json.convertValue(value.get("usage"), online.yudream.base.domain.platform.ai.valobj.AiUsage.class);
        value.put("usage", previousUsage.plus(result.usage()));
        return value;
    }

    private online.yudream.base.domain.platform.ai.valobj.HarnessRunResult respondNative(
            AiRun run,
            online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request, String requestId, Boolean approved,
            Map<String, Object> output, RunEmitter emitter, List<online.yudream.base.domain.platform.ai.service.AiAgentTool> tools) {
        var answers = List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(requestId, approved, output));
        if (tools == null) return harness.respond(request, answers, emitter::content, emitter::reasoning, emitter::tool,
                progress -> emitter.progress(progress.action(), progress));
        try (var scope = online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope.open(tools, run.getUserId())) {
            return harness.respond(request, answers, emitter::content, emitter::reasoning, emitter::tool,
                    progress -> emitter.progress(progress.action(), progress));
        }
    }

    private online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest requestFor(AiRun run, List<String> permissions) {
        return requestFor(run, permissions, true);
    }
    private online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest requestFor(AiRun run, List<String> permissions, boolean execution) {
        var ref = run.getNativeReference(); var profile = run.getNativeProfile();
        if (!profile.exactToolScopeCaptured()) throw new BizException("此历史检查点未记录准确的工具授权边界，请取消后继续会话");
        if (!profile.nativeActorCaptured()) throw new BizException("此历史检查点未记录原生调用身份，请取消后继续会话");
        if ("plugin-ai".equals(run.getModule()) && profile.invocationContext() == null)
            throw new BizException("此历史插件检查点未保存触发上下文，请取消后继续会话");
        if ("cms-studio".equals(ref.agentCode())) {
            if (studioTools == null) throw new BizException("站点设计器 AI 工具不可用");
            if (execution) studioTools.ensureAccess(permissions);
            else studioTools.ensureEnabled();
            if (execution && profile.allowedToolNames().stream().anyMatch(name -> name.equals("cms.studio.apply") || name.equals("cms_studio_apply"))
                    && !permissions.contains("*") && !permissions.contains("platform:cms:edit"))
                throw new BizException("站点设计器写入权限已被撤销");
            studioTools.contextKey(run.getUserId(), run.getSessionId());
        }
        var config = capabilityRepo.findByCode("ai").filter(online.yudream.base.domain.platform.capability.aggregate.CapabilityModule::enabled)
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力未启用"));
        return new online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest(profile.nativeUserId(), ref.sessionId(), ref.agentCode(),
                profile.systemPrompt(), "", List.of(), profile.providerCode(), profile.modelCode(), config,
                profile.toolCallingEnabled() && !profile.allowedToolNames().isEmpty(), permissions, List.of(), profile.allowedToolNames());
    }
    private static Long nativeActor(AiRun run) {
        var profile = run.getNativeProfile();
        if (profile != null && profile.nativeActorCaptured()) return profile.nativeUserId();
        return java.util.Objects.equals(run.getUserId(), 0L) ? null : run.getUserId();
    }
    private List<String> executionPermissions(AiRun run, List<String> callerPermissions) {
        Long actor = nativeActor(run);
        List<String> effective;
        if (java.util.Objects.equals(run.getUserId(), 0L) || !java.util.Objects.equals(actor, run.getUserId())) {
            if (actor == null || actor == 0L) effective = List.of();
            else {
                if (actorPermissions == null) throw new BizException("原 AI 调用身份的实时权限解析器不可用");
                effective = actorPermissions.currentPermissions(actor);
            }
        } else effective = callerPermissions == null ? List.of() : List.copyOf(callerPermissions);
        if (!java.util.Objects.equals(run.getUserId(), 0L)) {
            String permission = switch (run.getModule()) {
                case "chat" -> "platform:chat:use";
                case "cms", "cms-workspace" -> "platform:ai:generate";
                case "agent" -> run.getSessionId() != null && run.getSessionId().startsWith("agent-debug:") ? "platform:agent:edit" : "platform:agent:run";
                default -> null;
            };
            if (permission != null && !effective.contains("*") && !effective.contains(permission)) throw new BizException("原 AI 入口执行权限已被撤销");
        }
        return effective;
    }
    private online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway.Scope restoreInvocation(AiRun run, List<String> permissions) {
        var snapshot = run.getNativeProfile() == null ? null : run.getNativeProfile().invocationContext();
        if (snapshot == null) return () -> { };
        if (invocationContexts == null) throw new BizException("原 AI 触发上下文恢复适配器不可用");
        return invocationContexts.restore(snapshot, nativeActor(run), permissions);
    }

    private List<online.yudream.base.domain.platform.ai.service.AiAgentTool> restoredTools(AiRun run, RunEmitter emitter, List<String> permissions) {
        for (var continuation : continuations) {
            var restored = continuation.restoreTools(run, java.util.Set.copyOf(permissions), emitter);
            if (restored.isPresent()) return restored.get();
        }
        if ("cms-studio".equals(run.getNativeReference().agentCode())) {
            if (studioTools == null) throw new BizException("站点设计器 AI 工具不可用");
            return studioTools.bind(run.getUserId(), run.getSessionId(), permissions, emitter::frontendRequest);
        }
        if ("cms-canvas".equals(run.getNativeReference().agentCode())) return CmsCanvasClientTools.bind(CmsCanvasClientBridge.external(emitter::frontendRequest));
        if (run.getFrontendContext() != null) return AiFrontendTools.create(json.convertValue(run.getFrontendContext(),
                online.yudream.base.application.platform.ai.dto.AiFrontendContextDTO.class), emitter);
        return null;
    }

    private String logicalToolName(AiRun run, String requestId, String nativeName) {
        return snapshot(run).pendingFrontendTools().stream().filter(item -> requestId.equals(item.get("toolCallId")))
                .map(item -> String.valueOf(item.get("name"))).findFirst().orElse(nativeName);
    }

    private static Map<String, Object> frontendResponse(boolean ok, Map<String, Object> result, String error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", ok);
        response.put("result", result == null ? Map.of() : result);
        if (!ok) response.put("error", error == null ? "用户未执行该操作" : error.substring(0, Math.min(error.length(), 500)));
        return response;
    }

    private AiRun owned(Long userId, String id) {
        ensureEnabled();
        AiRun run = repo.find(id).orElseThrow(() -> new BizException("AI 运行不存在"));
        run.belongsTo(userId);
        return run;
    }

    private void ensureEnabled() { capabilities.ensureEnabled("ai", "AI 助手"); }

    private void heartbeat(RunEmitter emitter) {
        while (!emitter.terminal) {
            try { Thread.sleep(10_000); } catch (InterruptedException ignored) { return; }
            if (emitter.terminal) return;
            AiRun persisted = repo.find(emitter.run.getId()).orElse(null);
            if (persisted == null || persisted.getStatus().terminal() || persisted.isCancelRequested()) emitter.cancel();
            else repo.touch(emitter.run.getId());
            if (usageQuota != null) usageQuota.renew(emitter.run.getId());
        }
    }

    private void recoverStale(AiRun run) {
        if (run.getStatus() == AiRunStatus.RUNNING && run.getUpdatedAt().isBefore(Instant.now().minus(Duration.ofMinutes(2)))) {
            if (run.getNativeReference() != null && run.getNativeProfile() != null && run.getNativeProfile().nativeActorCaptured()
                    && harness != null && capabilityRepo != null) {
                try {
                    // 只读取框架日志；发现原生挂起时恢复投影，不提交输入或自动执行工具。
                    var state = harness.inspect(requestFor(run, List.of(), false));
                    if (!state.pendingInteractions().isEmpty() || !state.pendingConfirmations().isEmpty()) {
                        repo.nativeSnapshot(run.getId(), state);
                        if (repo.waitForInput(run.getId(), run.getResult())) {
                            run.setNativeSnapshot(state); run.setStatus(AiRunStatus.WAITING);
                            return;
                        }
                    }
                } catch (Exception error) { log.debug("原生任务检查失败，runId={}", run.getId()); }
            }
            if (repo.finish(run.getId(), AiRunStatus.FAILED, null, "服务运行已中断，已生成内容仍然保留，请继续会话")) {
                run.setStatus(AiRunStatus.FAILED);
                run.setError("服务运行已中断，已生成内容仍然保留，请继续会话");
            }
        }
    }

    private AiRunDTO snapshot(AiRun run) {
        final long head = run.getCursor();
        // 事件投影可跨连接恢复未核销的前端工具；已完成请求不会重复执行。
        Map<String, Map<String, Object>> requests = new LinkedHashMap<>();
        Map<String, Object> tools = new LinkedHashMap<>();
        Map<String, Object> activities = new LinkedHashMap<>();
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        StringBuilder structuredContent = new StringBuilder();
        String inputText = "";
        Map<String, GenerationProjection> generations = new LinkedHashMap<>();
        long cursor = 0;
        List<AiRunEvent> batch;
        do {
            batch = repo.events(run.getId(), cursor, 256);
            for (AiRunEvent event : batch) {
                if (event.cursor() > head) break;
                cursor = event.cursor();
                if (event.payload().get("callId") != null) {
                    String callId = String.valueOf(event.payload().get("callId"));
                    generations.computeIfAbsent(callId, GenerationProjection::new).accept(event);
                    continue;
                }
                if ("ai.input".equals(event.event())) inputText = String.valueOf(event.payload().getOrDefault("content", ""));
                if ("ai.structured".equals(event.event()) && "delta".equals(event.action())) {
                    structuredContent.append(event.payload().getOrDefault("content", ""));
                }
                if ("ai.message".equals(event.event())) {
                    Object delta = event.payload().getOrDefault("content", "");
                    if ("reasoning".equals(event.action())) reasoning.append(delta);
                    else if ("delta".equals(event.action())) content.append(delta);
                }
                if ("ai.tool".equals(event.event())) {
                    Object tool = event.payload().get("tool");
                    Map<?, ?> metadata = tool instanceof Map<?, ?> map ? map : Map.of();
                    tools.put(String.valueOf(metadata.getOrDefault("toolCallId", null)) + ":" + metadata.getOrDefault("toolName", null), tool);
                }
                if ("ai.progress".equals(event.event())) activities.put(event.action(), event.payload().get("activity"));
                if (!"ai.frontend".equals(event.event())) continue;
                Object id = event.payload().get("toolCallId");
                if ("request".equals(event.action())) requests.put(String.valueOf(id), event.payload());
                else requests.remove(String.valueOf(id));
            }
        } while (batch.size() == 256 && cursor < head);
        run.setContent(content.toString());
        run.setReasoning(reasoning.toString());
        List<Map<String, Object>> pending = requests.values().stream().map(request -> {
            Map<String, Object> value = new LinkedHashMap<>(request);
            value.put("responseRecorded", repo.frontendResponse(run.getId(), String.valueOf(request.get("toolCallId"))).isPresent());
            return value;
        }).toList();
        return AiRunAssembler.run(run, run.getStatus().terminal() ? List.of() : pending,
                new ArrayList<>(tools.values()), new ArrayList<>(activities.values()), structuredContent.toString(), inputText,
                generations.values().stream().map(GenerationProjection::value).toList());
    }

    private static final class GenerationProjection {
        private final String callId;
        private final StringBuilder content = new StringBuilder(), reasoning = new StringBuilder();
        private final Map<String, Object> activities = new LinkedHashMap<>(), tools = new LinkedHashMap<>();
        private boolean structured;
        private String providerCode, modelCode;
        private GenerationProjection(String callId) { this.callId = callId; }
        private void accept(AiRunEvent event) {
            var payload = event.payload();
            if (payload.get("providerCode") != null) providerCode = String.valueOf(payload.get("providerCode"));
            if (payload.get("modelCode") != null) modelCode = String.valueOf(payload.get("modelCode"));
            if (payload.get("structured") instanceof Boolean value) structured = value;
            if ("ai.generation".equals(event.event())) {
                if ("reasoning".equals(event.action())) reasoning.append(payload.getOrDefault("content", ""));
                else if ("delta".equals(event.action())) content.append(payload.getOrDefault("content", ""));
            } else if ("ai.progress".equals(event.event())) activities.put(event.action(), payload.get("activity"));
            else if ("ai.tool".equals(event.event())) {
                Object tool = payload.get("tool");
                Map<?, ?> metadata = tool instanceof Map<?, ?> map ? map : Map.of();
                tools.put(String.valueOf(metadata.get("toolCallId")) + ":" + metadata.get("toolName"), tool);
            }
        }
        private Map<String, Object> value() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("callId", callId); value.put("content", content.toString()); value.put("reasoning", reasoning.toString());
            value.put("structured", structured); value.put("providerCode", providerCode); value.put("modelCode", modelCode);
            value.put("activities", new ArrayList<>(activities.values())); value.put("tools", new ArrayList<>(tools.values()));
            return value;
        }
    }

    public final class RunEmitter {
        private final AiRun run;
        private final Runnable onCancel;
        private volatile boolean terminal;
        private volatile boolean waiting;
        private Thread worker;
        private RunEmitter(AiRun run, Runnable onCancel) { this.run = run; this.onCancel = onCancel; }
        public String id() { return run.getId(); }
        public boolean awaitingInteraction() { return waiting; }
        public void input(String text) { if (text != null) event("ai.input", "submitted", Map.of("content", text)); }
        private online.yudream.base.domain.platform.ai.service.AiGenerationObserverScope.Listener generationObserver() {
            return new online.yudream.base.domain.platform.ai.service.AiGenerationObserverScope.Listener() {
                public void content(String id, online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest request, String delta) {
                    if (delta == null || delta.isEmpty()) return;
                    var payload = generationMetadata(id, request); payload.put("content", delta); event("ai.generation", "delta", payload);
                }
                public void reasoning(String id, online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest request, String delta) {
                    if (delta == null || delta.isEmpty()) return;
                    var payload = generationMetadata(id, request); payload.put("content", delta); event("ai.generation", "reasoning", payload);
                }
                public void tool(String id, online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest request,
                                 online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult tool) {
                    var payload = generationMetadata(id, request);
                    Map<String, Object> metadata = toolMetadata(tool);
                    payload.put("tool", metadata); event("ai.tool", "pending".equals(tool.action()) ? "start" : "result", payload);
                }
                public void progress(String id, online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest request,
                                     online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress progress) {
                    var payload = generationMetadata(id, request); payload.put("activity", progress); event("ai.progress", progress.action(), payload);
                }
            };
        }
        private Map<String, Object> generationMetadata(String callId, online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest request) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("callId", callId); payload.put("structured", request.structuredOutput().enabled());
            payload.put("providerCode", request.providerCode()); payload.put("modelCode", request.modelCode());
            return payload;
        }
        public void allowNativeResume() { run.setNativeResumeSupported(true); }
        public void privateReference(online.yudream.base.domain.platform.ai.valobj.HarnessRunReference reference) {
            run.setNativeReference(reference);
            repo.nativeReference(run.getId(), reference, run.isNativeResumeSupported());
        }
        public void privateProfile(online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile profile) {
            run.setNativeProfile(profile);
            repo.nativeProfile(run.getId(), profile);
            if (profile.invocationContext() != null) privateInvocationContext(profile.invocationContext());
        }
        public void captureInvocationContext() {
            if (invocationContexts == null) return;
            var snapshot = invocationContexts.capture();
            if (snapshot != null) privateInvocationContext(snapshot);
        }
        public void privateInvocationContext(online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot context) {
            run.setInvocationContext(context); repo.invocationContext(run.getId(), context);
        }
        public void privateFrontendContext(online.yudream.base.application.platform.ai.dto.AiFrontendContextDTO context) {
            Map<String, Object> metadata = json.convertValue(context, new com.fasterxml.jackson.core.type.TypeReference<>() { });
            run.setFrontendContext(metadata); repo.frontendContext(run.getId(), metadata);
        }
        public void privateState(online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot state) {
            run.setNativeSnapshot(state); repo.nativeSnapshot(run.getId(), state);
            waiting = !state.pendingInteractions().isEmpty() || !state.pendingConfirmations().isEmpty();
            if (waiting) progress("waiting", Map.of("phase", "waiting", "status", "waiting", "title", "等待用户输入",
                    "pendingInteractions", state.pendingInteractions()));
        }
        public void content(String delta) {
            if (delta != null && !delta.isEmpty()) emit("ai.message", "delta", Map.of("content", delta), delta, null);
        }
        public void reasoning(String delta) {
            if (delta != null && !delta.isEmpty()) emit("ai.message", "reasoning", Map.of("content", delta), null, delta);
        }
        public void tool(Object tool) {
            Map<String, Object> metadata = toolMetadata(tool);
            boolean pending = "pending".equals(metadata.get("action"));
            emit("ai.tool", pending ? "start" : "result", Map.of("tool", metadata), null, null);
        }
        private Map<String, Object> toolMetadata(Object tool) {
            Map<String, Object> metadata = json.convertValue(tool, new com.fasterxml.jackson.core.type.TypeReference<>() { });
            if (metadata.get("payload") instanceof Map<?, ?> payload) {
                Object id = payload.get("_toolCallId") == null ? payload.get("toolCallId") : payload.get("_toolCallId");
                if (id != null) metadata.put("toolCallId", id);
            }
            metadata.putIfAbsent("toolCallId", UUID.randomUUID().toString());
            String action = String.valueOf(metadata.getOrDefault("action", ""));
            if (java.util.Set.of("error", "failed", "denied").contains(action)) metadata.put("status", "error");
            else metadata.putIfAbsent("status", "pending".equals(action) ? "executing" : "complete");
            return metadata;
        }
        public void progress(String phase, Object activity) { emit("ai.progress", phase, Map.of("activity", activity), null, null); }
        public void event(String event, String action, Map<String, Object> payload) { emit(event, action, payload, null, null); }
        public void frontendRequest(String requestId, String name, Map<String, Object> arguments) {
            repo.createFrontendRequest(run.getId(), requestId, name, arguments, null);
            event("ai.frontend", "request", Map.of("toolCallId", requestId, "name", name,
                    "arguments", arguments, "kind", "external_execution"));
        }

        public Map<String, Object> frontend(String name, Map<String, Object> arguments) {
            checkActive();
            String toolCallId = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plusSeconds(60);
            repo.createFrontendRequest(run.getId(), toolCallId, name, arguments, expiresAt);
            event("ai.frontend", "request", Map.of("toolCallId", toolCallId, "name", name,
                    "arguments", arguments, "expiresAt", Instant.now().plusSeconds(60).toString()));
            Map<String, Object> response;
            try {
                response = null;
                while (Instant.now().isBefore(expiresAt)) {
                    checkActive();
                    response = repo.frontendResponse(run.getId(), toolCallId).orElse(null);
                    if (response != null) break;
                    Thread.sleep(350);
                }
                if (response == null) response = Map.of("ok", false, "error", "前端未在时限内确认操作");
            }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                response = Map.of("ok", false, "error", "前端操作等待已取消");
            } catch (Exception error) { response = Map.of("ok", false, "error", "前端未在时限内确认操作"); }
            finally { repo.expireFrontend(run.getId(), toolCallId); }
            if (!terminal) event("ai.frontend", "settled", Map.of("toolCallId", toolCallId, "response", response));
            return response;
        }

        private synchronized void emit(String event, String action, Map<String, Object> payload, String text, String reasoning) {
            checkActive();
            if (text != null) run.setContent(run.getContent() + text);
            if (reasoning != null) run.setReasoning(run.getReasoning() + reasoning);
            long cursor = run.getCursor() + 1;
            Map<String, Object> safePayload = json.convertValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            try {
                repo.append(new AiRunEvent(run.getId(), cursor, event, action, run.getModule(), run.getId(), Instant.now(), safePayload),
                        text == null ? null : run.getContent(), reasoning == null ? null : run.getReasoning());
            } catch (RuntimeException error) {
                terminal = true;
                stopNative();
                if (onCancel != null) onCancel.run();
                if (worker != null) worker.interrupt();
                throw new BizException("运行日志保存失败，已提交内容仍然保留");
            }
            run.setCursor(cursor);
        }
        public void checkActive() {
            if (terminal || Thread.currentThread().isInterrupted()) throw new BizException("AI 运行已取消");
        }
        private synchronized void complete(Object result) {
            if (terminal) return;
            for (var projector : projectors) result = projector.project(run, result, waiting);
            if (waiting) {
                repo.waitForInput(run.getId(), result == null ? null : json.convertValue(result, Object.class));
                terminal = true;
                return;
            }
            event("ai.result", "complete", Map.of("result", result == null ? Map.of() : result));
            repo.finish(run.getId(), AiRunStatus.COMPLETED, result == null ? null : json.convertValue(result, Object.class), null);
            terminal = true;
        }
        private synchronized void fail(String message) {
            if (terminal) return;
            event("ai.error", "failed", Map.of("message", message == null ? "AI 运行失败" : message));
            repo.finish(run.getId(), AiRunStatus.FAILED, null, message);
            terminal = true;
        }
        private synchronized void cancel() {
            if (terminal) return;
            stopNative();
            // 取消只由显式用例或实例间取消信号触发。
            event("ai.error", "cancelled", Map.of("message", "运行已取消"));
            repo.finish(run.getId(), AiRunStatus.CANCELLED, null, "运行已取消");
            terminal = true;
            for (var projector : projectors) projector.cancel(run);
            for (var continuation : continuations) continuation.cancel(run);
            try { if (onCancel != null) onCancel.run(); }
            finally { if (worker != null) worker.interrupt(); }
        }
        private void stopNative() {
            var reference = run.getNativeReference();
            if (reference == null || harness == null) return;
            if ((run.getStatus() == AiRunStatus.WAITING || waiting) && run.getNativeProfile() != null) {
                var profile = run.getNativeProfile();
                var config = capabilityRepo.findByCode("ai").map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig()).orElse(Map.of());
                var request = new online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest(nativeActor(run), reference.sessionId(), reference.agentCode(),
                        profile.systemPrompt(), "", List.of(), profile.providerCode(), profile.modelCode(), config, false, List.of(), List.of(), List.of());
                var state = harness.cancelPending(request, reference.turnId());
                run.setNativeSnapshot(state); repo.nativeSnapshot(run.getId(), state);
            } else harness.interrupt(nativeActor(run), reference.sessionId(), reference.agentCode());
        }
    }

}
