package online.yudream.base.application.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import online.yudream.base.domain.platform.ai.enumerate.AiRunStatus;
import online.yudream.base.domain.platform.ai.repo.AiRunRepo;
import online.yudream.base.domain.platform.ai.valobj.AiRunEvent;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunReference;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AiRunAppServiceTest {
    @Test
    void generationContinuesWithoutSubscriberAndReplaysOnlyAfterCursor() throws Exception {
        MemoryRepo repo = new MemoryRepo();
        AiRunAppService service = service(repo);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var receipt = service.start(7L, "chat", "chat-1", out -> {
            out.content("真实"); out.reasoning("推理"); out.tool(Map.of("toolName", "web.fetch"));
            entered.countDown(); await(release); out.content("流式"); return Map.of("content", "真实流式");
        }, null);
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var snapshot = service.get(7L, receipt.id());
        assertEquals("真实", snapshot.content());
        assertEquals("推理", snapshot.reasoning());
        assertEquals(1, snapshot.tools().size());
        release.countDown(); waitTerminal(service, receipt.id());
        var resumed = service.events(7L, receipt.id(), snapshot.cursor());
        assertEquals(List.of("ai.message", "ai.result"), resumed.stream().map(AiRunEvent::event).toList());
        assertEquals("真实流式", service.get(7L, receipt.id()).content());
        assertEquals(AiRunStatus.COMPLETED, service.get(7L, receipt.id()).status());
    }

    @Test
    void snapshotUsesFixedCommittedPrefixEvenWhenRunAdvancesDuringRead() {
        MemoryRepo repo = new MemoryRepo();
        AiRun run = repo.create(AiRun.start(7L, "session", "wiki", "instance"));
        repo.append(event(run, 1, "ai.message", "delta", Map.of("content", "已提交")), "已提交", null);
        repo.beforeRead = () -> {
            repo.append(event(run, 2, "ai.message", "delta", Map.of("content", "新尾部")), "已提交新尾部", null);
            repo.append(event(run, 3, "ai.tool", "result", Map.of("tool", Map.of("toolName", "tail"))), null, null);
        };
        var snapshot = service(repo).get(7L, run.getId());
        assertEquals(1, snapshot.cursor());
        assertEquals("已提交", snapshot.content());
        assertTrue(snapshot.tools().isEmpty());
    }

    @Test
    void failedCommitDoesNotReplayOrphanEventOrContinueGeneration() throws Exception {
        MemoryRepo repo = new MemoryRepo();
        repo.failCursor = 3;
        AtomicBoolean continued = new AtomicBoolean();
        var service = service(repo);
        var receipt = service.start(7L, "chat", "session", out -> {
            out.content("安全前缀"); out.content("未提交"); continued.set(true); return Map.of();
        }, null);
        waitTerminal(service, receipt.id());
        var snapshot = service.get(7L, receipt.id());
        assertEquals(AiRunStatus.FAILED, snapshot.status());
        assertEquals("安全前缀", snapshot.content());
        assertEquals(2, snapshot.cursor());
        assertFalse(continued.get());
        assertEquals(2, service.events(7L, receipt.id(), 0).size());
    }

    @Test
    void frontendResultCanBeAcknowledgedOnAnotherInstanceAndOnlyOnce() throws Exception {
        MemoryRepo repo = new MemoryRepo();
        var first = service(repo); var second = service(repo);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        var receipt = first.start(7L, "chat", "session", out -> {
            result.set(out.frontend("ui.navigate", Map.of("routeName", "users"))); return Map.of();
        }, null);
        Map<String, Object> pending = null;
        for (int i = 0; i < 100 && pending == null; i++) {
            var snapshot = second.get(7L, receipt.id());
            if (!snapshot.pendingFrontendTools().isEmpty()) pending = snapshot.pendingFrontendTools().getFirst();
            else Thread.sleep(10);
        }
        assertNotNull(pending);
        String toolId = String.valueOf(pending.get("toolCallId"));
        assertThrows(BizException.class, () -> second.frontendResult(9L, receipt.id(), toolId, true, Map.of(), null));
        second.frontendResult(7L, receipt.id(), toolId, true, Map.of("routeName", "users"), null);
        assertThrows(BizException.class, () -> second.frontendResult(7L, receipt.id(), toolId, true, Map.of(), null));
        waitTerminal(first, receipt.id());
        assertEquals(true, result.get().get("ok"));
        assertTrue(first.get(7L, receipt.id()).pendingFrontendTools().isEmpty());
    }

    @Test
    void unauthorizedReadAndCancelAreRejectedAndExplicitCancelStopsWorker() throws Exception {
        MemoryRepo repo = new MemoryRepo();
        var service = service(repo);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(), hook = new AtomicBoolean();
        var receipt = service.start(7L, "chat", "session", out -> {
            entered.countDown();
            try { Thread.sleep(20_000); } catch (InterruptedException error) { interrupted.set(true); Thread.currentThread().interrupt(); }
            return Map.of();
        }, () -> hook.set(true));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertThrows(BizException.class, () -> service.get(9L, receipt.id()));
        assertThrows(BizException.class, () -> service.events(9L, receipt.id(), 0));
        assertThrows(BizException.class, () -> service.cancel(9L, receipt.id()));
        service.cancel(7L, receipt.id());
        for (int i=0;i<100&&!interrupted.get();i++) Thread.sleep(10);
        assertTrue(interrupted.get()); assertTrue(hook.get());
        assertEquals(AiRunStatus.CANCELLED, service.get(7L, receipt.id()).status());
        assertEquals("cancelled", service.events(7L, receipt.id(), 0).getLast().action());
    }

    @Test
    void inlineQueueExecutionKeepsTheExistingWorkerAndSeparatesStructuredOutput() {
        MemoryRepo repo = new MemoryRepo();
        var service = service(repo);
        Thread caller = Thread.currentThread();
        String result = service.execute(7L, "wiki", "ingest", out -> {
            assertSame(caller, Thread.currentThread());
            out.event("ai.structured", "delta", Map.of("content", "{\"plan\":true}"));
            return "结果";
        }, null);
        assertEquals("结果", result);
        var snapshot = service.list(7L, "ingest", false).getFirst();
        assertEquals("", snapshot.content());
        assertEquals("{\"plan\":true}", snapshot.structuredContent());
        assertEquals(AiRunStatus.COMPLETED, snapshot.status());
    }

    @Test
    void internalModelObservationAndRealInputRestoreSeparatelyFromMainAnswer() {
        MemoryRepo repo = new MemoryRepo(); var service = service(repo);
        var request = new online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest("不能公开的提示", "内部提取",
                (String)null,"provider","model",Map.of("apiKey","不能公开的密钥"))
                .withStructuredOutput(online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput.jsonObject());
        service.execute(7L,"wiki","ingest",out -> {
            assertEquals(7L,online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope.currentUserId());
            out.input("真实用户输入");
            var observation = online.yudream.base.domain.platform.ai.service.AiGenerationObserverScope.capture(request);
            assertNotNull(observation);
            observation.content("{\"nodes\":"); observation.reasoning("真实模型推理"); observation.content("[]}");
            observation.tool(new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult("extract","complete","","完成",Map.of()));
            out.content("主要答案"); return Map.of();
        },null);
        var snapshot = service.list(7L,"ingest",false).getFirst();
        assertEquals("真实用户输入",snapshot.inputText()); assertEquals("主要答案",snapshot.content());
        assertEquals("",snapshot.reasoning()); assertTrue(snapshot.tools().isEmpty());
        assertEquals(1,snapshot.generations().size()); var generation=snapshot.generations().getFirst();
        assertEquals("{\"nodes\":[]}",generation.get("content")); assertEquals("真实模型推理",generation.get("reasoning"));
        assertEquals(true,generation.get("structured")); assertEquals("provider",generation.get("providerCode"));
        assertFalse(snapshot.toString().contains("不能公开"));
    }

    @Test
    void systemWaitingCancellationTerminatesNativePendingUsingOriginalAnonymousIdentity() throws Exception {
        MemoryRepo repo = new MemoryRepo(); AtomicBoolean cancelled = new AtomicBoolean();
        var harness = new online.yudream.base.domain.platform.ai.service.HarnessAgentGateway() {
            public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult run(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request, java.util.function.Consumer<String> delta,
                    java.util.function.Consumer<String> reasoning, java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool) {
                throw new AssertionError("取消不调用模型");
            }
            public online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot cancelPending(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request, String turnId) {
                assertNull(request.userId()); assertEquals("system-session",request.sessionId()); assertEquals("turn",turnId);
                assertFalse(request.toolCallingEnabled()); assertTrue(request.permissionCodes().isEmpty());
                cancelled.set(true);
                return new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(2,"turn","native","cancelled",List.of(),List.of(),List.of());
            }
        };
        var modules = (online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class<?>[]{online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class},(proxy,method,args)->Optional.empty());
        var runs = new AiRunAppService(repo,enabled(),new ObjectMapper(),harness,modules,null,List.of());
        var receipt = runs.start(0L,"plugin-ai","background",out -> {
            out.privateReference(new HarnessRunReference("system-agent","system-session","turn","native"));
            out.privateProfile(new online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile("provider","model","",true,List.of("write_tool"),true,null,true));
            out.privateState(new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(1,"turn","native","suspended",List.of(),List.of(),
                    List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessPendingInteraction("confirm-1","confirmation","write_tool",Map.of()))));
            assertTrue(out.awaitingInteraction()); return Map.of();
        },null);
        for(int i=0;i<200&&runs.get(0L,receipt.id()).status()!=AiRunStatus.WAITING;i++)Thread.sleep(10);
        var admin = new AiSystemRunAppService(runs);
        assertTrue(runs.list(7L,null,false).isEmpty());
        assertThrows(BizException.class,()->admin.get(7L,List.of("platform:chat:use"),receipt.id()));
        assertThrows(BizException.class,()->admin.cancel(7L,List.of("platform:ai:system:view"),receipt.id()));
        assertEquals(AiRunStatus.CANCELLED,admin.cancel(7L,List.of("platform:ai:system:edit"),receipt.id()).status());
        assertTrue(cancelled.get()); assertTrue(runs.get(0L,receipt.id()).pendingInteractions().isEmpty());
    }

    @Test
    void systemApprovalUsesTheOriginalActorsLivePermissionsAcrossEveryContinuationBoundary() throws Exception {
        for (Long nativeActor : new Long[]{null, 7L}) {
            MemoryRepo repo = new MemoryRepo();
            List<String> expected = nativeActor == null ? List.of() : List.of("original:current:permission");
            var original = new online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot(
                    "external-user", "connection", "channel", "message", "MENTION", "trace", List.of("read.one"), List.of(), null);
            AiRun run = AiRun.start(0L, "background", "plugin-ai", "old-instance");
            run.setStatus(AiRunStatus.WAITING);
            run.setNativeReference(new HarnessRunReference("plugin-agent", "native-session", "turn", "native"));
            run.setNativeProfile(new online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile(
                    "provider", "model", "", true, List.of("read_one"), true, nativeActor, true, original));
            run.setNativeSnapshot(new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(
                    1, "turn", "native", "suspended", List.of(), List.of(), List.of(
                    new online.yudream.base.domain.platform.ai.valobj.HarnessPendingInteraction("approval", "confirmation", "read_one", Map.of()))));
            repo.create(run);
            var invoked = new java.util.concurrent.atomic.AtomicInteger();
            var harness = new online.yudream.base.domain.platform.ai.service.HarnessAgentGateway() {
                public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult run(
                        online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request, java.util.function.Consumer<String> delta,
                        java.util.function.Consumer<String> reasoning, java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool) {
                    throw new AssertionError("回应不能提交新输入");
                }
                public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult respond(
                        online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request,
                        List<online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer> answers,
                        java.util.function.Consumer<String> delta, java.util.function.Consumer<String> reasoning,
                        java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool,
                        java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> progress) {
                    assertEquals(nativeActor, request.userId()); assertEquals(expected, request.permissionCodes());
                    assertEquals(List.of("read_one"), request.allowedToolNames()); assertTrue(answers.getFirst().approved());
                    invoked.incrementAndGet(); delta.accept("原调用者续答");
                    online.yudream.base.domain.platform.ai.service.HarnessExecutionScope.reportState(
                            new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(2, "turn", "native", "completed", List.of(), List.of(), List.of()));
                    return new online.yudream.base.domain.platform.ai.valobj.HarnessRunResult("原调用者续答", "",
                            online.yudream.base.domain.platform.ai.valobj.AiUsage.empty(), List.of());
                }
            };
            var module = online.yudream.base.domain.platform.capability.aggregate.CapabilityModule.builder()
                    .code("ai").enabled(true).config(Map.of()).build();
            var modules = (online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class},
                    (proxy, method, args) -> Optional.of(module));
            var service = new AiRunAppService(repo, enabled(), new ObjectMapper(), harness, modules, null, List.of());
            service.actorPermissions(actor -> { assertEquals(7L, actor); return expected; });
            var restored = new java.util.concurrent.atomic.AtomicInteger();
            service.invocationContexts(new online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway() {
                public online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot capture() { return null; }
                public Scope restore(online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot snapshot, Long actor, List<String> permissions) {
                    assertEquals(original, snapshot); assertEquals(nativeActor, actor); assertEquals(expected, permissions);
                    restored.incrementAndGet(); return () -> { };
                }
            });
            var preflight = new AtomicBoolean(); var continued = new AtomicBoolean();
            service.continuations(List.of(new AiRunContinuation() {
                public Optional<List<online.yudream.base.domain.platform.ai.service.AiAgentTool>> restoreTools(
                        AiRun current, java.util.Set<String> permissions, AiRunAppService.RunEmitter emitter) {
                    assertEquals(java.util.Set.copyOf(expected), permissions); preflight.set(true); return Optional.empty();
                }
                public Optional<Object> continueAfterNative(AiRun current, online.yudream.base.domain.platform.ai.valobj.HarnessRunResult result,
                        java.util.Set<String> permissions, AiRunAppService.RunEmitter emitter) {
                    assertEquals(java.util.Set.copyOf(expected), permissions); continued.set(true); return Optional.empty();
                }
            }));
            var admin = new AiSystemRunAppService(service);
            assertThrows(BizException.class, () -> admin.answer(99L, List.of("platform:ai:system:view"), run.getId(), "approval", true, null));
            admin.answer(99L, List.of("*"), run.getId(), "approval", true, null);
            for (int i = 0; i < 200 && !service.get(0L, run.getId()).status().terminal(); i++) Thread.sleep(10);
            assertEquals(AiRunStatus.COMPLETED, service.get(0L, run.getId()).status());
            assertEquals(1, invoked.get()); assertEquals(2, restored.get()); assertTrue(preflight.get()); assertTrue(continued.get());
        }
    }

    @Test
    void restartAfterNativePauseBeforeProjectionCommitRestoresWaitingWithoutSubmittingInput() {
        MemoryRepo repo = new MemoryRepo(); AiRun run = AiRun.start(7L,"session","chat","dead-instance");
        run.setNativeReference(new HarnessRunReference("general","chat-session","turn","native"));
        run.setNativeProfile(new online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile("provider","model","",true,List.of("ui_navigate"),true,7L,true));
        run.setUpdatedAt(Instant.now().minusSeconds(121)); repo.create(run);
        var pending = new online.yudream.base.domain.platform.ai.valobj.HarnessPendingInteraction("request","external_execution","ui_navigate",Map.of("routeName","users"));
        var harness = new online.yudream.base.domain.platform.ai.service.HarnessAgentGateway() {
            public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult run(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request, java.util.function.Consumer<String> delta,
                    java.util.function.Consumer<String> reasoning, java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool) {
                throw new AssertionError("只读恢复不提交输入");
            }
            public online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot inspect(online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request) {
                assertEquals(7L,request.userId());
                return new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(1,"turn","native","suspended",List.of(),List.of(),List.of(pending));
            }
        };
        var module = online.yudream.base.domain.platform.capability.aggregate.CapabilityModule.builder().code("ai").enabled(true).config(Map.of()).build();
        var modules = (online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class<?>[]{online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class},(proxy,method,args)->Optional.of(module));
        var service = new AiRunAppService(repo,enabled(),new ObjectMapper(),harness,modules,null,List.of());
        var snapshot = service.get(7L,run.getId());
        assertEquals(AiRunStatus.WAITING,snapshot.status()); assertEquals(List.of(pending),snapshot.pendingInteractions());
        assertTrue(service.events(7L,run.getId(),0).isEmpty());
    }

    @Test
    void unboundPluginObservationChecksOriginalConnectionActorChannelAndTrace() {
        MemoryRepo repo = new MemoryRepo(); var service = service(repo);
        var original = new online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot("external-user","connection","channel","message","MENTION","trace",List.of(),List.of(),null);
        service.execute(0L,"plugin-ai","source",out->{out.privateInvocationContext(original);out.content("该来源的私有结果");return Map.of();},null);
        String id=service.list(0L,"source",false).getFirst().id();
        assertEquals("该来源的私有结果",service.getInvocationRun(0L,id,original).content());
        assertThrows(BizException.class,()->service.getInvocationRun(0L,id,null));
        for(int changed=0;changed<4;changed++) {
            var wrong = new online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot(changed==0?"other":original.platformUserId(),
                    changed==1?"other":original.connectionId(),changed==2?"other":original.channelId(),original.messageId(),original.trigger(),
                    changed==3?"other":original.traceId(),List.of(),List.of(),null);
            assertThrows(BizException.class,()->service.getInvocationRun(0L,id,wrong));
        }
        service.execute(0L,"wiki","public",out->Map.of(),null);
        String publicId=service.list(0L,"public",false).getFirst().id();
        assertThrows(BizException.class,()->service.getInvocationRun(0L,publicId,original));
        assertFalse(service.getInvocationRun(0L,id,original).toString().contains("external-user"));
    }

    @Test
    void respondingRestoresInvocationContextForBothToolPreflightAndTheWholeNativeContinuation() throws Exception {
        MemoryRepo repo = new MemoryRepo(); ThreadLocal<Boolean> restored = ThreadLocal.withInitial(()->false);
        java.util.concurrent.atomic.AtomicInteger restoreCount=new java.util.concurrent.atomic.AtomicInteger();
        var original = new online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot("external-user","connection","channel","message","MENTION","trace",List.of("read.one"),List.of(),null);
        var harness = new online.yudream.base.domain.platform.ai.service.HarnessAgentGateway() {
            public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult run(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request,java.util.function.Consumer<String> delta,
                    java.util.function.Consumer<String> reasoning,java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool) {throw new AssertionError();}
            public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult respond(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request,List<online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer> answers,
                    java.util.function.Consumer<String> delta,java.util.function.Consumer<String> reasoning,java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> progress) {
                assertTrue(restored.get()); assertEquals(List.of("fresh:permission"),request.permissionCodes()); delta.accept("续答");
                online.yudream.base.domain.platform.ai.service.HarnessExecutionScope.reportState(new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(2,"turn","native","completed",List.of(),List.of(),List.of()));
                return new online.yudream.base.domain.platform.ai.valobj.HarnessRunResult("续答","",online.yudream.base.domain.platform.ai.valobj.AiUsage.empty(),List.of());
            }
        };
        var module=online.yudream.base.domain.platform.capability.aggregate.CapabilityModule.builder().code("ai").enabled(true).config(Map.of()).build();
        var modules=(online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class},(proxy,method,args)->Optional.of(module));
        var service=new AiRunAppService(repo,enabled(),new ObjectMapper(),harness,modules,null,List.of());
        service.invocationContexts(new online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway() {
            public online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot capture(){return original;}
            public Scope restore(online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot snapshot,Long actor,List<String> permissions){
                assertEquals(original,snapshot);assertEquals(7L,actor);assertEquals(List.of("fresh:permission"),permissions);
                restoreCount.incrementAndGet();boolean prior=restored.get();restored.set(true);return ()->restored.set(prior);
            }
        });
        service.continuations(List.of(new AiRunContinuation() {
            public Optional<List<online.yudream.base.domain.platform.ai.service.AiAgentTool>> restoreTools(AiRun run,java.util.Set<String> permissions,AiRunAppService.RunEmitter emitter){assertTrue(restored.get());return Optional.empty();}
            public Optional<Object> continueAfterNative(AiRun run,online.yudream.base.domain.platform.ai.valobj.HarnessRunResult result,java.util.Set<String> permissions,AiRunAppService.RunEmitter emitter){assertTrue(restored.get());return Optional.empty();}
        }));
        var receipt=service.start(7L,"plugin-ai","source",out->{
            out.privateReference(new HarnessRunReference("workflow","native-session","turn","native"));
            out.privateProfile(new online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile("provider","model","",true,List.of("read.one"),true,7L,true,original));
            out.privateState(new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(1,"turn","native","suspended",List.of(),List.of(),List.of(
                    new online.yudream.base.domain.platform.ai.valobj.HarnessPendingInteraction("approval","confirmation","read_one",Map.of()))));return Map.of();
        },null);
        for(int i=0;i<200&&service.get(7L,receipt.id()).status()!=AiRunStatus.WAITING;i++)Thread.sleep(10);
        service.respond(7L,receipt.id(),"approval",true,null,List.of("fresh:permission")); waitTerminal(service,receipt.id());
        assertEquals(2,restoreCount.get()); assertFalse(restored.get()); assertEquals("续答",service.get(7L,receipt.id()).content());
    }

    @Test
    void checkpointResumePreservesOriginalModelAndToolBoundaryWithFreshPermissions() throws Exception {
        MemoryRepo repo = new MemoryRepo();
        AiRun run = repo.create(AiRun.start(7L, "session", "chat", "old-instance"));
        run.setNativeReference(new HarnessRunReference("general", "chat-session", "native-turn", "native-run"));
        run.setNativeProfile(new online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile("original-provider", "original-model",
                "原始系统提示", false, List.of("read_only_tool"), true, 7L, true));
        run.setNativeResumeSupported(true);
        repo.nativeReference(run.getId(), run.getNativeReference(), true);
        repo.nativeProfile(run.getId(), run.getNativeProfile());
        repo.finish(run.getId(), AiRunStatus.FAILED, null, "实例中断");
        AtomicReference<online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest> resumed = new AtomicReference<>();
        var harness = new online.yudream.base.domain.platform.ai.service.HarnessAgentGateway() {
            public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult run(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request,
                    java.util.function.Consumer<String> delta, java.util.function.Consumer<String> reasoning,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool) {
                throw new AssertionError("恢复不得重新 submit");
            }
            public online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot inspect(online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request) {
                return new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(1,"native-turn","native-run","RUNNING",List.of(),List.of());
            }
            public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult resume(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request, String turnId,
                    java.util.function.Consumer<String> delta, java.util.function.Consumer<String> reasoning,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> progress) {
                resumed.set(request); assertEquals("native-turn",turnId); delta.accept("接续正文");
                return new online.yudream.base.domain.platform.ai.valobj.HarnessRunResult("接续正文","",online.yudream.base.domain.platform.ai.valobj.AiUsage.empty(),List.of());
            }
        };
        var module = online.yudream.base.domain.platform.capability.aggregate.CapabilityModule.builder().code("ai")
                .enabled(true)
                .config(Map.of("defaultProvider","changed-provider","defaultModel","changed-model","apiKey","secret-never-persisted")).build();
        var modules = (online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class},
                (proxy,method,args)->Optional.of(module));
        var service = new AiRunAppService(repo, enabled(), new ObjectMapper(), harness, modules, null, List.of());
        assertThrows(BizException.class,()->service.resume(7L,run.getId(),List.of("fresh:permission:view")));
        assertEquals(AiRunStatus.FAILED,service.get(7L,run.getId()).status());
        service.resume(7L,run.getId(),List.of("fresh:permission:view","platform:chat:use")); waitTerminal(service,run.getId());
        assertEquals("original-provider",resumed.get().providerCode()); assertEquals("original-model",resumed.get().modelCode());
        assertEquals("原始系统提示",resumed.get().systemPrompt()); assertFalse(resumed.get().toolCallingEnabled());
        assertEquals(List.of("read_only_tool"),resumed.get().allowedToolNames());
        assertEquals(List.of("fresh:permission:view","platform:chat:use"),resumed.get().permissionCodes());
        assertEquals("接续正文",service.get(7L,run.getId()).content());
        assertFalse(service.get(7L,run.getId()).toString().contains("secret-never-persisted"));
    }

    @Test
    void nativeFrontendPauseSurvivesWorkerExitAndAnotherInstanceCanRespondWithoutResubmitting() throws Exception {
        MemoryRepo repo = new MemoryRepo();
        var context = new online.yudream.base.application.platform.ai.dto.AiFrontendContextDTO(null,
                List.of(new online.yudream.base.application.platform.ai.dto.AiFrontendContextDTO.Route("users","用户","/system/user")),List.of());
        AtomicReference<List<String>> permissions = new AtomicReference<>();
        var harness = new online.yudream.base.domain.platform.ai.service.HarnessAgentGateway() {
            public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult run(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request,
                    java.util.function.Consumer<String> delta, java.util.function.Consumer<String> reasoning,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool) { throw new AssertionError("不得重新提交输入"); }
            public online.yudream.base.domain.platform.ai.valobj.HarnessRunResult respond(
                    online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest request,
                    List<online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer> answers,
                    java.util.function.Consumer<String> delta, java.util.function.Consumer<String> reasoning,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tool,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> progress) {
                permissions.set(request.permissionCodes()); assertEquals("external-1",answers.getFirst().requestId());
                assertEquals(true,answers.getFirst().output().get("ok"));
                assertNotNull(online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope.currentTools());
                delta.accept("完成定位");
                online.yudream.base.domain.platform.ai.service.HarnessExecutionScope.reportState(
                        new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(2,"turn","native","completed",List.of(),List.of(),List.of()));
                return new online.yudream.base.domain.platform.ai.valobj.HarnessRunResult("完成定位","",online.yudream.base.domain.platform.ai.valobj.AiUsage.empty(),List.of());
            }
        };
        var module = online.yudream.base.domain.platform.capability.aggregate.CapabilityModule.builder().code("ai")
                .enabled(true).config(Map.of()).build();
        var modules = (online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class}, (proxy,method,args)->Optional.of(module));
        var first = new AiRunAppService(repo,enabled(),new ObjectMapper(),harness,modules,null,List.of());
        var receipt = first.start(7L,"chat","session",out -> {
            out.privateFrontendContext(context);
            out.privateProfile(new online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile("provider","model","提示",true,List.of("ui.navigate"),true,7L,true));
            out.privateReference(new HarnessRunReference("general","chat-session","turn","native"));
            var tool = (online.yudream.base.domain.platform.ai.service.AiExternalAgentTool) AiFrontendTools.create(context,out).getFirst();
            assertThrows(UnsupportedOperationException.class,()->tool.execute(new online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall("ui.navigate",Map.of("routeName","users"))));
            out.tool(tool.prepare("external-1",new online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall("ui.navigate",Map.of("routeName","users"))));
            out.privateState(new online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot(1,"turn","native","suspended",List.of(),List.of(),
                    List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessPendingInteraction("external-1","external_execution","ui.navigate",Map.of("routeName","users")))));
            return Map.of("content","");
        },null);
        for(int i=0;i<200&&first.get(7L,receipt.id()).status()!=AiRunStatus.WAITING;i++)Thread.sleep(10);
        var paused=first.get(7L,receipt.id()); assertEquals(AiRunStatus.WAITING,paused.status());
        assertFalse(first.events(7L,receipt.id(),0).stream().anyMatch(event->"ai.result".equals(event.event())));
        assertEquals(1,paused.pendingFrontendTools().size()); assertEquals(1,paused.pendingInteractions().size());
        // 模拟工具执行结果已落盘，应用恰在调用 native.respond 之前退出。
        assertTrue(repo.acknowledgeFrontend(receipt.id(),"external-1",Map.of("ok",true,"result",Map.of("routeName","users"))));
        var restarted = new AiRunAppService(repo,enabled(),new ObjectMapper(),harness,modules,null,List.of());
        assertEquals(true,restarted.get(7L,receipt.id()).pendingFrontendTools().getFirst().get("responseRecorded"));
        restarted.frontendResult(7L,receipt.id(),"external-1",false,Map.of("routeName","不能替换原结果"),"不能覆盖已落盘结果",List.of("platform:chat:use"));
        waitTerminal(restarted,receipt.id());
        assertEquals(List.of("platform:chat:use"),permissions.get());
        assertEquals("完成定位",restarted.get(7L,receipt.id()).content());
        assertEquals(AiRunStatus.COMPLETED,restarted.get(7L,receipt.id()).status());
        assertThrows(BizException.class,()->restarted.frontendResult(7L,receipt.id(),"external-1",true,Map.of(),null));
    }

    private static AiRunAppService service(MemoryRepo repo) {
        return new AiRunAppService(repo, enabled(), new ObjectMapper(), (request, delta, reasoning, tool) -> null, null, null, List.of());
    }
    private static CapabilityAppService enabled() {
        return new CapabilityAppService(null, List.of()) {
            @Override public void ensureEnabled(String code, String name) { }
        };
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("等待测试释放"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
    private static void waitTerminal(AiRunAppService service, String id) throws Exception {
        for (int i=0;i<200;i++) { if (service.get(7L,id).status().terminal()) return; Thread.sleep(10); }
        fail("运行未终结");
    }
    private static AiRunEvent event(AiRun run, long cursor, String type, String action, Map<String,Object> payload) {
        return new AiRunEvent(run.getId(), cursor, type, action, run.getModule(), run.getId(), Instant.now(), payload);
    }
    private static class MemoryRepo implements AiRunRepo {
        final Map<String,AiRun> runs = new LinkedHashMap<>();
        final List<AiRunEvent> log = new ArrayList<>();
        final Map<String,Map<String,Object>> responses = new LinkedHashMap<>();
        final Map<String,Instant> expiries = new LinkedHashMap<>();
        long failCursor = -1;
        Runnable beforeRead;
        public synchronized AiRun create(AiRun run) { runs.put(run.getId(), copy(run)); return run; }
        public synchronized Optional<AiRun> find(String id) { return Optional.ofNullable(runs.get(id)).map(MemoryRepo::copy); }
        public synchronized List<AiRun> list(Long userId,String sessionId,boolean active) {
            return runs.values().stream().filter(r -> r.getUserId().equals(userId) && (sessionId==null || sessionId.equals(r.getSessionId())))
                    .filter(r -> !active || !r.getStatus().terminal()).map(MemoryRepo::copy).toList();
        }
        public synchronized void append(AiRunEvent event,String content,String reasoning) {
            log.add(event); AiRun run=runs.get(event.runId());
            if (event.cursor()==failCursor) { finish(event.runId(),AiRunStatus.FAILED,null,"保存失败"); throw new IllegalStateException("模拟事件落盘后 head 写失败"); }
            assertEquals(run.getCursor()+1,event.cursor()); run.setCursor(event.cursor());
            if(content!=null)run.setContent(content); if(reasoning!=null)run.setReasoning(reasoning); run.setUpdatedAt(Instant.now());
        }
        public synchronized boolean finish(String id,AiRunStatus status,Object result,String error) {
            AiRun run=runs.get(id); if(run.getStatus().terminal())return false;
            run.setStatus(status);run.setResult(result);run.setError(error);return true;
        }
        public synchronized void touch(String id){runs.get(id).setUpdatedAt(Instant.now());}
        public synchronized void requestCancel(String id){runs.get(id).setCancelRequested(true);}
        public synchronized void nativeReference(String id,HarnessRunReference ref,boolean supported){runs.get(id).setNativeReference(ref);runs.get(id).setNativeResumeSupported(supported);}
        public synchronized boolean reopen(String id,String owner){
            AiRun run=runs.get(id);if(run.getStatus()!=AiRunStatus.FAILED)return false;
            run.setStatus(AiRunStatus.RUNNING);run.setOwnerInstance(owner);run.setError(null);return true;
        }
        public synchronized void nativeProfile(String id,online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile profile){runs.get(id).setNativeProfile(profile);}
        public synchronized void nativeSnapshot(String id,online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot state){runs.get(id).setNativeSnapshot(state);}
        public synchronized void frontendContext(String id,Map<String,Object> context){runs.get(id).setFrontendContext(context);}
        public synchronized void invocationContext(String id,online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot context){runs.get(id).setInvocationContext(context);}
        public synchronized boolean waitForInput(String id,Object result){runs.get(id).setStatus(AiRunStatus.WAITING);runs.get(id).setResult(result);return true;}
        public synchronized boolean continueWaiting(String id,String owner){
            AiRun run=runs.get(id);if(run.getStatus()!=AiRunStatus.WAITING)return false;run.setStatus(AiRunStatus.RUNNING);run.setOwnerInstance(owner);return true;
        }
        public synchronized void createFrontendRequest(String id,String tool,String name,Map<String,Object> args,Instant expires){expiries.put(id+tool,expires);}
        public synchronized boolean acknowledgeFrontend(String id,String tool,Map<String,Object> response){
            if(!expiries.containsKey(id+tool)||responses.containsKey(id+tool)||(expiries.get(id+tool)!=null&&expiries.get(id+tool).isBefore(Instant.now())))return false;
            responses.put(id+tool,response);return true;
        }
        public synchronized Optional<Map<String,Object>> frontendResponse(String id,String tool){return Optional.ofNullable(responses.get(id+tool));}
        public synchronized void expireFrontend(String id,String tool){expiries.remove(id+tool);}
        public synchronized List<AiRunEvent> events(String id,long after,int limit){
            if(beforeRead!=null){Runnable action=beforeRead;beforeRead=null;action.run();}
            long head=runs.get(id).getCursor();
            return log.stream().filter(e->e.runId().equals(id)&&e.cursor()>after&&e.cursor()<=head).limit(limit).toList();
        }
        public synchronized List<AiRun> stale(Instant before){return List.of();}
        private static AiRun copy(AiRun a){
            AiRun b=new AiRun();b.setId(a.getId());b.setUserId(a.getUserId());b.setSessionId(a.getSessionId());b.setModule(a.getModule());
            b.setOwnerInstance(a.getOwnerInstance());b.setStatus(a.getStatus());b.setCursor(a.getCursor());b.setContent(a.getContent());b.setReasoning(a.getReasoning());
            b.setCreatedAt(a.getCreatedAt());b.setUpdatedAt(a.getUpdatedAt());b.setResult(a.getResult());b.setError(a.getError());b.setCancelRequested(a.isCancelRequested());
            b.setNativeReference(a.getNativeReference());b.setNativeResumeSupported(a.isNativeResumeSupported());b.setNativeProfile(a.getNativeProfile());
            b.setNativeSnapshot(a.getNativeSnapshot());b.setFrontendContext(a.getFrontendContext());b.setInvocationContext(a.getInvocationContext());return b;
        }
    }
}
