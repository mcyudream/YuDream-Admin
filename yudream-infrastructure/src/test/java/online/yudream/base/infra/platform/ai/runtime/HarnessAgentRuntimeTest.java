package online.yudream.base.infra.platform.ai.runtime;

import com.mongodb.client.MongoClients;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.HarnessExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiUsageQuotaGateway;
import online.yudream.base.domain.platform.ai.valobj.AiUsageReservation;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunReference;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.infra.platform.ai.service.AiClientProperties;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderType;
import online.yudream.base.infra.platform.ai.service.provider.AbstractOpenAiCompatibleProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.ResolvedAiModel;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Harness 运行时集成测试：无网络 FakeModel + 内存 Mongo 桩，验证
 * 会话状态跨轮次持久化（框架 AgentState）、增量回调与按次工具开关。
 */
class HarnessAgentRuntimeTest extends MongoStoreTestBase {

    Path workspace;

    private RecordingFakeModel fake;
    private HarnessAgentGateway runtime;

    @BeforeEach
    void initRuntime() throws java.io.IOException {
        workspace = java.nio.file.Files.createTempDirectory("agent-workspace");
        fake = new RecordingFakeModel("你好");
        runtime = runtimeFor(workspace);
    }

    private HarnessAgentRuntime runtimeFor(Path directory, online.yudream.base.domain.platform.ai.service.AiAgentTool... globalTools) {
        AgentScopeModels models = new AgentScopeModels(new AiClientProperties()) {
            @Override
            public Model resolve(ResolvedAiModel resolved) {
                return fake;
            }
        };
        AiProviderAdapter adapter = new AbstractOpenAiCompatibleProviderAdapter() {
            @Override
            public AiProviderType type() {
                return AiProviderType.OPENAI_COMPATIBLE;
            }
        };
        HarnessRuntimeProperties properties = new HarnessRuntimeProperties();
        properties.setWorkspaceDir(directory.toString());
        properties.setMemoryHooksEnabled(false);
        HarnessAgentRuntime agentRuntime = new HarnessAgentRuntime(
                models,
                new AiProviderConfigParser(),
                List.of(adapter),
                new AiClientProperties(),
                properties,
                new MongoAgentStateStore(mongo),
                new MongoBaseStore(mongo),
                new AgentPermissionGateway() {
                    @Override
                    public boolean hasPermission(String permissionCode) {
                        return true;
                    }

                    @Override
                    public boolean hasPermission(String permissionCode, List<String> permissionCodes, boolean explicitContext) {
                        return permissionCode == null || permissionCode.isBlank()
                                || permissionCodes.contains("*") || permissionCodes.contains(permissionCode);
                    }
                },
                new ObjectProvider<online.yudream.base.domain.platform.ai.service.AiAgentTool>() {
                    @Override
                    public online.yudream.base.domain.platform.ai.service.AiAgentTool getObject() {
                        return globalTools.length == 0 ? null : globalTools[0];
                    }

                    @Override
                    public Stream<online.yudream.base.domain.platform.ai.service.AiAgentTool> stream() {
                        return java.util.Arrays.stream(globalTools);
                    }
                },
                new MongoAgentSkillRepository(mongo));
        return agentRuntime;
    }

    @Test
    void secondTurnSeesFirstTurnContextThroughNativeSessionLog() {
        HarnessRunRequest first = new HarnessRunRequest(
                1L, "s-1", "general", "测试提示词", "我叫天宇",
                List.of(), "test", "fake-model", Map.of("providers", PROVIDERS_JSON),
                false, List.of(), List.of());
        HarnessRunResult firstResult = runtime.run(first, null, null, null);
        assertEquals("你好", firstResult.content());
        assertTrue(fake.firstCallTexts().stream().anyMatch(text -> text.contains("我叫天宇")),
                "first turn should contain user message: " + fake.firstCallTexts());
        assertTrue(fake.firstCallTexts().stream().anyMatch(text -> text.contains("测试提示词")),
                "system prompt override should reach the model");

        HarnessRunRequest second = new HarnessRunRequest(
                1L, "s-1", "general", "测试提示词", "我叫什么名字？",
                List.of(), "test", "fake-model", Map.of("providers", PROVIDERS_JSON),
                false, List.of(), List.of());
        HarnessRunResult secondResult = runtime.run(second, null, null, null);
        assertEquals("你好", secondResult.content());

        List<String> secondCallTexts = fake.callTexts(1);
        assertTrue(secondCallTexts.stream().anyMatch(text -> text.contains("我叫天宇")),
                "framework should restore first-turn user message from AgentState: " + secondCallTexts);
        assertTrue(secondCallTexts.stream().anyMatch(text -> text.contains("你好")),
                "framework should restore first-turn assistant reply from AgentState: " + secondCallTexts);
        assertTrue(secondCallTexts.stream().anyMatch(text -> text.contains("我叫什么名字？")),
                "second turn should contain new user message");
    }

    @Test
    void differentSessionsDoNotShareContext() {
        HarnessRunRequest alice = new HarnessRunRequest(
                1L, "s-a", "general", "提示词", "天宇的问题", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
        runtime.run(alice, null, null, null);

        HarnessRunRequest bob = new HarnessRunRequest(
                2L, "s-a", "general", "提示词", "天宇是谁", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
        runtime.run(bob, null, null, null);

        List<String> bobCallTexts = fake.callTexts(1);
        assertTrue(bobCallTexts.stream().noneMatch(text -> text.contains("天宇的问题")),
                "different users must not share session context: " + bobCallTexts);
    }

    @Test
    void toolCallingDisabledHidesAllToolSchemas() {
        HarnessRunRequest request = new HarnessRunRequest(
                1L, "s-tools", "general", "提示词", "看看工具", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
        runtime.run(request, null, null, null);
        assertTrue(fake.lastTools().isEmpty(), "toolCallingEnabled=false must hide tools from the model");
    }

    @Test
    void differentReplicaWorkspaceRecoversNativeJournalWithoutLocalFiles() throws java.io.IOException {
        HarnessRunResult first = runtime.run(request("s-replica"), null, null, null);
        HarnessAgentGateway replica = runtimeFor(java.nio.file.Files.createTempDirectory("agent-replica"));
        assertEquals(first.nativeTurnId(), replica.inspect(request("s-replica")).turnId());
        replica.run(request("s-replica"), null, null, null);
        assertEquals(2, fake.callMessages.get(1).stream().filter(msg -> msg.getRole() == MsgRole.USER).count(),
                "另一副本没有本机workspace时仍恢复同一原生上下文");
    }

    @Test
    void anonymousMemoryIsIsolatedBySessionWhileAuthenticatedMemorySharesOnlyWithinUser() {
        var selection = new AgentModelSelection(fake, GenerateOptions.builder().build());
        try (var agent = ((HarnessAgentRuntime) runtime).buildAgent("general", selection, true)) {
            var first = agent.workspaceFor("_anon", "anon-a");
            assertTrue(first.getFilesystem().write(io.agentscope.core.agent.RuntimeContext.empty(),
                    "MEMORY.md", "仅第一位访客可见").isSuccess());
            assertEquals("仅第一位访客可见", first.readManagedWorkspaceFileUtf8(
                    io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md"));
            assertEquals("", agent.workspaceFor("_anon", "anon-b").readManagedWorkspaceFileUtf8(
                    io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md"));
        }
        try (var agent = ((HarnessAgentRuntime) runtime).buildAgent("general", selection)) {
            agent.workspaceFor("1", "user-a").getFilesystem().write(io.agentscope.core.agent.RuntimeContext.empty(),
                    "MEMORY.md", "用户一的记忆");
            assertEquals("用户一的记忆", agent.workspaceFor("1", "user-b").readManagedWorkspaceFileUtf8(
                    io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md"));
            assertEquals("", agent.workspaceFor("2", "user-b").readManagedWorkspaceFileUtf8(
                    io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md"));
        }
    }

    @Test
    void trueDeltasReasoningAndNativeReferenceReachApplicationBeforeCompletion() {
        fake.thinking = "需要先分析";
        List<String> deltas = new CopyOnWriteArrayList<>();
        List<String> reasoning = new CopyOnWriteArrayList<>();
        List<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> progress = new CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicReference<HarnessRunReference> reference = new java.util.concurrent.atomic.AtomicReference<>();
        HarnessRunResult result;
        try (HarnessExecutionScope ignored = HarnessExecutionScope.open("request-1", reference::set)) {
            result = runtime.run(request("s-events"), deltas::add, reasoning::add, null, progress::add);
        }
        assertEquals(List.of("你", "好"), deltas);
        assertEquals("需要先分析", String.join("", reasoning));
        assertEquals("你好", result.content());
        assertNotNull(reference.get());
        assertEquals(result.nativeTurnId(), reference.get().turnId());
        assertEquals(result.nativeRunId(), reference.get().runId());
        assertTrue(progress.stream().anyMatch(item -> "analysis".equals(item.action())));
        assertTrue(mongo.rows.keySet().stream().anyMatch(key -> key.contains("__agentscope_session_log_v1__")));
        int callsBeforeRead = fake.calls.size();
        var snapshot = runtime.inspect(request("s-events"));
        assertEquals("completed", snapshot.status());
        assertEquals(result.nativeTurnId(), snapshot.turnId());
        assertTrue(snapshot.cursor() > 0);
        assertEquals(callsBeforeRead, fake.calls.size(), "读取恢复摘要不能再执行模型");
    }

    @Test
    void nativeIdempotencyDoesNotRepeatModelExecutionOrStreamReplayAsGeneration() {
        HarnessRunResult first;
        HarnessRunResult retried;
        List<String> retryDeltas = new ArrayList<>();
        try (HarnessExecutionScope ignored = HarnessExecutionScope.open("fixed-request", null)) {
            first = runtime.run(request("s-once"), null, null, null);
            retried = runtime.run(request("s-once"), retryDeltas::add, null, null);
        }
        assertEquals(1, fake.calls.size());
        assertEquals(first.content(), retried.content());
        assertEquals(first.nativeTurnId(), retried.nativeTurnId());
        assertTrue(retryDeltas.isEmpty(), "历史回放应由读取出口负责，不能冒充模型生成");
    }

    @Test
    void scopedFrontendToolsAreNotRetainedByNextAgentInstance() {
        var tool = new online.yudream.base.domain.platform.ai.service.AiAgentTool() {
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor descriptor() {
                return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor(
                        "ui.navigate", "页面跳转", "跳转到当前用户有权限的页面", null,
                        "页面跳转", "助手工具", "跳转", Map.of("path", "路径"), null);
            }
            @Override public online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk risk() {
                return online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.READ;
            }
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult execute(
                    online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall call) {
                throw new AssertionError("测试模型不应调用工具");
            }
        };
        HarnessRunRequest request = new HarnessRunRequest(1L, "s-scoped", "general", "提示词", "找到页面",
                List.of(), "test", "fake-model", Map.of("providers", PROVIDERS_JSON), true,
                List.of(), List.of(), List.of("ui.navigate"));
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(tool))) {
            runtime.run(request, null, null, null);
            assertEquals(List.of("ui_navigate"), fake.lastTools().stream().map(ToolSchema::getName).toList());
        }
        runtime.run(request, null, null, null);
        assertTrue(fake.lastTools().isEmpty());
    }

    @Test
    void scopedPublicAliasRemainsStableAcrossNativeInstancesAndLaterAdminGrants() throws java.io.IOException {
        AtomicInteger privateCalls = new AtomicInteger();
        AtomicInteger publicCalls = new AtomicInteger();
        var privateTool = searchTool("platform:wiki:search", privateCalls);
        var publicTool = searchTool(null, publicCalls);
        HarnessAgentGateway first = runtimeFor(workspace, privateTool, publicTool);
        fake.toolName = "wiki_search_public";
        var request = new HarnessRunRequest(1L, "public-alias", "general", "提示词", "公开知识检索", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), true, List.of(), List.of(), List.of("wiki_search_public"));
        var profile = new java.util.concurrent.atomic.AtomicReference<online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile>();
        try (AiAgentToolExecutionScope tools = AiAgentToolExecutionScope.open(List.of(publicTool));
             HarnessExecutionScope scope = HarnessExecutionScope.open("public-alias-run", null, profile::set)) {
            first.run(request, null, null, null);
        }
        assertEquals(List.of("wiki_search_public"), profile.get().allowedToolNames());
        assertEquals(1, publicCalls.get()); assertEquals(0, privateCalls.get());
        var granted = new HarnessRunRequest(1L, "public-alias", "general", "提示词", "继续公开检索", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), true, List.of("*"), List.of(), profile.get().allowedToolNames());
        var replica = runtimeFor(java.nio.file.Files.createTempDirectory("public-alias-replica"), privateTool, publicTool);
        replica.run(granted, null, null, null);
        assertEquals(List.of("wiki_search_public"), fake.lastTools().stream().map(ToolSchema::getName).toList());
        assertEquals(0, privateCalls.get(), "公开别名不能被权限不同的管理工具认领");
    }

    private online.yudream.base.domain.platform.ai.service.AiAgentTool searchTool(String permission, AtomicInteger calls) {
        return new online.yudream.base.domain.platform.ai.service.AiAgentTool() {
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor descriptor() {
                return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor("wiki.search", "知识检索", "检索",
                        permission, "检索", "知识库", "检索", Map.of(), null);
            }
            @Override public online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk risk() {
                return online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.READ;
            }
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult execute(
                    online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall call) {
                calls.incrementAndGet();
                return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult("wiki.search", "search", permission,
                        "检索完成", Map.of("public", permission == null));
            }
        };
    }

    @Test
    void quotaIsReservedForTrustedUserAndSettledFromRealUsage() {
        List<AiUsage> settled = new ArrayList<>();
        ((HarnessAgentRuntime) runtime).setUsageQuotaGateway(new AiUsageQuotaGateway() {
            @Override public AiUsageReservation reserve(Long userId, long tokenBudget) {
                assertEquals(1L, userId);
                assertEquals(4096, tokenBudget);
                return new AiUsageReservation("r-1", userId, java.time.LocalDate.now(), tokenBudget);
            }
            @Override public void settle(AiUsageReservation reservation, AiUsage usage) { settled.add(usage); }
        });
        HarnessRunResult result = runtime.run(request("s-quota"), null, null, null);
        assertEquals(15, result.usage().totalTokens());
        assertEquals(List.of(result.usage()), settled);
    }

    @Test
    void defaultModelUsesCurrentPersistedProviderConfigAndStoresItsActualCodes() {
        var profile = new java.util.concurrent.atomic.AtomicReference<online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile>();
        var request = new HarnessRunRequest(1L, "s-default", "general", "提示词", "你好", List.of(),
                null, null, Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
        try (HarnessExecutionScope ignored = HarnessExecutionScope.open("default-request", null, profile::set)) {
            assertEquals("你好", runtime.run(request, null, null, null).content());
        }
        assertEquals("test", profile.get().providerCode());
        assertEquals("fake-model", profile.get().modelCode());
        assertTrue(profile.get().exactToolScopeCaptured());
        assertTrue(profile.get().nativeActorCaptured());
        assertEquals(1L, profile.get().nativeUserId());
        assertTrue(profile.get().allowedToolNames().isEmpty());
        assertTrue(!profile.get().toString().contains("sk-test"));
    }

    @Test
    void anonymousNativeActorIsCapturedExplicitlyInsteadOfUsingUiSystemOwner() {
        var profile = new java.util.concurrent.atomic.AtomicReference<online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile>();
        var request = new HarnessRunRequest(null, "anon-profile", "general", "提示词", "你好", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
        try (HarnessExecutionScope ignored = HarnessExecutionScope.open("anonymous-actor", null, profile::set)) {
            runtime.run(request, null, null, null);
        }
        assertTrue(profile.get().nativeActorCaptured());
        org.junit.jupiter.api.Assertions.assertNull(profile.get().nativeUserId());
        assertEquals("completed", runtime.inspect(request).status());
    }

    @Test
    void explicitInterruptStopsNativeTaskAndResumeUsesItsCheckpoint() throws Exception {
        fake.hang = true;
        var ready = new java.util.concurrent.CountDownLatch(1);
        var reference = new java.util.concurrent.atomic.AtomicReference<HarnessRunReference>();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var worker = executor.submit(() -> {
                try (HarnessExecutionScope ignored = HarnessExecutionScope.open("stop-request", reference::set)) {
                    return runtime.run(request("s-stop"), delta -> ready.countDown(), null, null);
                }
            });
            assertTrue(ready.await(15, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(runtime.interrupt(1L, "s-stop", "general"));
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> worker.get(25, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("interrupted", runtime.inspect(request("s-stop")).status());
            fake.hang = false;
            HarnessRunResult resumed = runtime.resume(request("s-stop"), reference.get().turnId(), null, null, null, null);
            assertEquals(reference.get().turnId(), resumed.nativeTurnId());
            assertEquals("completed", runtime.inspect(request("s-stop")).status());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void externalExecutionWaitsInNativeJournalAndRespondsAfterOriginalAgentHasClosed() {
        fake.toolName = "ui_navigate";
        fake.toolInput = Map.of("path", "/system/user");
        AtomicInteger prepared = new AtomicInteger();
        var tool = new online.yudream.base.domain.platform.ai.service.AiExternalAgentTool() {
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor descriptor() {
                return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor(
                        "ui.navigate", "页面跳转", "跳转到已授权页面", null, "页面跳转", "助手工具", "跳转",
                        Map.of("path", "路径"), null);
            }
            @Override public online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk risk() {
                return online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.READ;
            }
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult prepare(String requestId,
                    online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall call) {
                prepared.incrementAndGet();
                assertEquals("call-external", requestId);
                return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult(
                        "ui.navigate", "requested", null, "等待页面跳转", Map.of("requestId", requestId, "path", "/system/user"));
            }
        };
        var request = new HarnessRunRequest(1L, "s-external", "general", "提示词", "打开用户页面",
                List.of(), "test", "fake-model", Map.of("providers", PROVIDERS_JSON), true,
                List.of(), List.of(), List.of("ui.navigate"));
        var state = new java.util.concurrent.atomic.AtomicReference<online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot>();
        HarnessRunResult suspended;
        try (AiAgentToolExecutionScope tools = AiAgentToolExecutionScope.open(List.of(tool));
             HarnessExecutionScope execution = HarnessExecutionScope.open("external-request", null, null, state::set)) {
            suspended = runtime.run(request, null, null, null);
        }
        assertEquals("suspended", suspended.status());
        assertEquals("suspended", state.get().status());
        assertEquals(1, prepared.get());
        assertEquals(1, fake.calls.size());
        assertEquals("external_execution", suspended.pendingInteractions().getFirst().kind());
        var pending = runtime.inspect(request).pendingInteractions().getFirst();
        assertEquals("call-external", pending.requestId());
        assertEquals("/system/user", pending.arguments().get("path"));
        var anotherUser = new HarnessRunRequest(2L, "s-external", "general", "提示词", "", List.of(), "test", "fake-model",
                Map.of("providers", PROVIDERS_JSON), true, List.of(), List.of(), List.of("ui.navigate"));
        assertTrue(runtime.inspect(anotherUser).pendingInteractions().isEmpty());
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> runtime.respond(anotherUser, List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(
                        pending.requestId(), null, Map.of("ok", true))), null, null, null, null));
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> runtime.respond(request, List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(
                        "unknown", null, Map.of("ok", true))), null, null, null, null));
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> runtime.respond(request, List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(
                        pending.requestId(), true, null)), null, null, null, null));
        HarnessRunResult resumed;
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(tool))) {
            resumed = runtime.respond(request, List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(
                    pending.requestId(), null, Map.of("ok", true, "path", "/system/user"))), null, null, null, null);
        }
        assertEquals("completed", resumed.status());
        assertEquals(suspended.nativeTurnId(), resumed.nativeTurnId());
        assertTrue(runtime.inspect(request).pendingInteractions().isEmpty());
        assertEquals(2, fake.calls.size());
        assertEquals(1, prepared.get(), "回复不能再次执行已公布的外部工具请求");
        assertTrue(fake.callMessages.get(1).stream().flatMap(msg -> msg.getContentBlocks(
                io.agentscope.core.message.ToolResultBlock.class).stream()).anyMatch(block ->
                block.getId().equals(pending.requestId()) && block.getOutput().stream().anyMatch(content ->
                        content instanceof TextBlock text && text.getText().contains("\"ok\":true"))));
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> runtime.respond(request, List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(
                        pending.requestId(), null, Map.of("ok", true))), null, null, null, null));
    }

    @Test
    void pendingWriteApprovalIsPersistentAndFreshPermissionRevocationPreventsExecution() {
        fake.toolName = "demo_write";
        AtomicInteger executed = new AtomicInteger();
        var tool = new online.yudream.base.domain.platform.ai.service.AiAgentTool() {
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor descriptor() {
                return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor("demo.write", "写入", "测试写入",
                        "platform:demo:edit", "写入", "测试", "写入", Map.of(), null);
            }
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult execute(
                    online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall call) {
                executed.incrementAndGet();
                return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult("demo.write", "write", null, "已写入", Map.of());
            }
        };
        var request = new HarnessRunRequest(1L, "s-approval", "general", "提示词", "写入", List.of(), "test", "fake-model",
                Map.of("providers", PROVIDERS_JSON), true, List.of("platform:demo:edit"), List.of(), List.of("demo.write"));
        HarnessRunResult suspended;
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(tool))) {
            suspended = runtime.run(request, null, null, null);
        }
        assertEquals("suspended", suspended.status());
        assertEquals(0, executed.get());
        assertEquals(1, runtime.inspect(request).pendingConfirmations().size());
        var revoked = new HarnessRunRequest(1L, "s-approval", "general", "提示词", "", List.of(), "test", "fake-model",
                Map.of("providers", PROVIDERS_JSON), true, List.of(), List.of(), List.of("demo.write"));
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(tool))) {
            var finished = runtime.respond(revoked, List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(
                    suspended.pendingConfirmations().getFirst().toolCallId(), true, null)), null, null, null, null);
            assertEquals("completed", finished.status());
        }
        assertEquals(0, executed.get(), "审批不能覆盖恢复时的权限撤销");
    }

    @Test
    void nativePendingCancellationSurvivesNewReplicaAndRejectsLateAnswers() throws java.io.IOException {
        fake.toolName = "demo_write";
        AtomicInteger executed = new AtomicInteger();
        var tool = new online.yudream.base.domain.platform.ai.service.AiAgentTool() {
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor descriptor() {
                return new online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor(
                        "demo.write", "写入", "写入", null, "写入", "测试", "写入", Map.of(), null);
            }
            @Override public online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult execute(
                    online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall call) {
                executed.incrementAndGet();
                throw new AssertionError("取消的工具不能执行");
            }
        };
        var request = new HarnessRunRequest(1L, "s-cancel", "general", "提示词", "写入", List.of(), "test", "fake-model",
                Map.of("providers", PROVIDERS_JSON), true, List.of(), List.of(), List.of("demo.write"));
        HarnessRunResult paused;
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(tool))) {
            paused = runtime.run(request, null, null, null);
        }
        HarnessAgentGateway replica = runtimeFor(java.nio.file.Files.createTempDirectory("agent-cancel-replica"));
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> replica.cancelPending(request, "another-turn"));
        assertEquals("cancelled", replica.cancelPending(request, paused.nativeTurnId()).status());
        assertEquals("cancelled", runtime.cancelPending(request, paused.nativeTurnId()).status());
        assertTrue(runtime.inspect(request).pendingInteractions().isEmpty());
        assertEquals(0, executed.get());
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> runtime.respond(request, List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(
                        paused.pendingConfirmations().getFirst().toolCallId(), true, null)), null, null, null, null));
    }

    @Test
    void fabricatedHiddenToolCannotExecuteEvenWithWildcardPermissions() {
        fake.toolName = "wiki_search";
        AtomicInteger privateCalls = new AtomicInteger();
        AtomicInteger publicCalls = new AtomicInteger();
        var replica = runtimeFor(workspace, searchTool("platform:wiki:search", privateCalls), searchTool(null, publicCalls));
        var request = new HarnessRunRequest(1L, "hidden-call", "general", "提示词", "公开检索", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), true, List.of("*"), List.of(), List.of("wiki_search_public"));
        var cards = new ArrayList<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult>();
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> replica.run(request, null, null, cards::add));
        assertEquals(0, privateCalls.get());
        assertEquals(0, publicCalls.get());
        assertEquals(List.of("wiki_search_public"), fake.lastTools().stream().map(ToolSchema::getName).toList());
        assertTrue(cards.stream().anyMatch(card -> "denied".equals(card.action())
                && "call-external".equals(card.payload().get("_toolCallId"))));
    }

    @Test
    void fabricatedCallCannotBypassDisabledTools() {
        fake.toolName = "wiki_search";
        AtomicInteger calls = new AtomicInteger();
        var replica = runtimeFor(workspace, searchTool(null, calls));
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> replica.run(request("disabled-call"), null, null, null));
        assertTrue(fake.lastTools().isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void pluginDestructiveToolUsesNativePendingApprovalAndExecutesOnceOnNewReplica() throws Exception {
        fake.toolName = "qbank_wipe";
        AtomicInteger executed = new AtomicInteger();
        var registry = new online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry();
        registry.register("qbank", new online.yudream.base.plugin.spi.system.ai.PluginAiTool() {
            @Override public online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor descriptor() {
                return new online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor("qbank.wipe", "清理", "危险操作",
                        "plugin:qbank:delete", online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk.DESTRUCTIVE,
                        false, java.util.Set.of("MENTION"), Map.of());
            }
            @Override public online.yudream.base.plugin.spi.system.ai.PluginAiToolResult execute(
                    online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext context,
                    online.yudream.base.plugin.spi.system.ai.PluginAiToolCall call) {
                assertEquals("connection-1", context.connectionId());
                assertEquals("channel-1", context.channelId());
                executed.incrementAndGet();
                return new online.yudream.base.plugin.spi.system.ai.PluginAiToolResult("deleted", "已清理", Map.of());
            }
        });
        var context = new online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext(1L, null, "connection-1",
                "channel-1", "message-1", "MENTION", "trace-1", List.of("plugin:qbank:delete"), List.of("qbank.wipe"), List.of("qbank.wipe"));
        var original = runtimeFor(workspace);
        original.setPluginTools(registry);
        var request = new HarnessRunRequest(1L, "plugin-approval", "general", "提示词", "清理", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), true,
                List.of("plugin:qbank:delete"), List.of(), List.of("qbank.wipe"));
        HarnessRunResult pending;
        online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.set(context);
        try (var scoped = AiAgentToolExecutionScope.open(List.of(new online.yudream.base.infra.platform.plugin.service.PluginAgentToolGateway(registry).pluginTool("qbank.wipe")))) {
            pending = original.run(request, null, null, null);
        }
        finally { online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.clear(); }
        assertEquals("suspended", pending.status());
        assertEquals(0, executed.get());
        assertEquals("confirmation", pending.pendingInteractions().getFirst().kind());
        var replica = runtimeFor(java.nio.file.Files.createTempDirectory("plugin-native-replica"));
        replica.setPluginTools(registry);
        online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.set(context);
        try (var scoped = AiAgentToolExecutionScope.open(List.of(new online.yudream.base.infra.platform.plugin.service.PluginAgentToolGateway(registry).pluginTool("qbank.wipe")))) {
            var completed = replica.respond(request, List.of(new online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer(
                    pending.pendingInteractions().getFirst().requestId(), true, null)), null, null, null, null);
            assertEquals("completed", completed.status());
            assertEquals(pending.nativeTurnId(), completed.nativeTurnId());
        } finally { online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.clear(); }
        assertEquals(1, executed.get());
        assertTrue(replica.inspect(request).pendingInteractions().isEmpty());
    }

    @Test
    void scopedPluginBridgePreservesReadConfirmationAndCannotExposeOtherGrantedPluginTools() {
        fake.toolName = "qbank_search";
        var registry = new online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry();
        var calls = new AtomicInteger();
        registry.register("qbank", readPluginTool("qbank.search", true, calls));
        registry.register("qbank", readPluginTool("qbank.extra", false, calls));
        var replica = runtimeFor(workspace); replica.setPluginTools(registry);
        var context = new online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext(1L, null, "conn", "channel", "msg",
                "MENTION", "trace", List.of(), List.of("qbank.search", "qbank.extra"), List.of());
        var request = new HarnessRunRequest(1L, "plugin-read-approval", "general", "提示词", "查询", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), true, List.of(), List.of(), List.of());
        online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.set(context);
        try {
            var bridge = new online.yudream.base.infra.platform.plugin.service.PluginAgentToolGateway(registry).pluginTool("qbank.search");
            assertTrue(bridge.requiresConfirmation());
            try (var scoped = AiAgentToolExecutionScope.open(List.of(bridge))) {
                var waiting = replica.run(request, null, null, null);
                assertEquals("suspended", waiting.status());
                assertEquals("confirmation", waiting.pendingInteractions().getFirst().kind());
                assertEquals(List.of("qbank_search"), fake.lastTools().stream().map(ToolSchema::getName)
                        .filter(name -> name.startsWith("qbank_")).toList());
                assertEquals(0, calls.get());
                assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                        () -> bridge.execute(new online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall("qbank.search", Map.of())));
                assertEquals(0, calls.get(), "固定业务节点不能绕过插件工具的审批语义");
                replica.cancelPending(request, waiting.nativeTurnId());
            }
        } finally { online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.clear(); }
    }

    @Test
    void selectedPluginBridgeWithoutOriginalInvocationContextFailsClosed() {
        var registry = new online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry();
        registry.register("qbank", readPluginTool("qbank.search", false, new AtomicInteger()));
        var replica = runtimeFor(workspace); replica.setPluginTools(registry);
        var bridge = new online.yudream.base.infra.platform.plugin.service.PluginAgentToolGateway(registry).pluginTool("qbank.search");
        try (var scoped = AiAgentToolExecutionScope.open(List.of(bridge))) {
            assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                    () -> replica.run(request("missing-plugin-context"), null, null, null));
        }
        assertTrue(fake.calls.isEmpty());
    }

    @Test
    void unrelatedHostToolCannotBeReplacedByPluginWithSameSafeName() {
        var registry = new online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry();
        registry.register("qbank", readPluginTool("wiki.search", false, new AtomicInteger()));
        var replica = runtimeFor(workspace, searchTool(null, new AtomicInteger())); replica.setPluginTools(registry);
        online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.set(
                new online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext(1L, null, "conn", "channel", "msg",
                        "MENTION", "trace", List.of(), List.of("wiki.search"), List.of()));
        try {
            assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                    () -> replica.run(request("name-conflict"), null, null, null));
        } finally { online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.clear(); }
        assertTrue(fake.calls.isEmpty());
    }

    private online.yudream.base.plugin.spi.system.ai.PluginAiTool readPluginTool(String name, boolean confirmation, AtomicInteger calls) {
        return new online.yudream.base.plugin.spi.system.ai.PluginAiTool() {
            @Override public online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor descriptor() {
                return new online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor(name, name, "只读检索", null,
                        online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk.READ, confirmation, java.util.Set.of("MENTION"), Map.of());
            }
            @Override public online.yudream.base.plugin.spi.system.ai.PluginAiToolResult execute(
                    online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext context,
                    online.yudream.base.plugin.spi.system.ai.PluginAiToolCall call) {
                calls.incrementAndGet(); return new online.yudream.base.plugin.spi.system.ai.PluginAiToolResult("complete", "完成", Map.of());
            }
        };
    }

    private HarnessRunRequest request(String sessionId) {
        return new HarnessRunRequest(1L, sessionId, "general", "测试提示词", "你好",
                List.of(), "test", "fake-model", Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
    }

    @Test
    void nativeWorkspaceWriteCannotBypassCapturedQqSandboxGuard() {
        fake.toolName = "write_file";
        fake.toolInput = Map.of("path", "native-guard.txt", "content", "不允许写入");
        var replica = runtimeFor(workspace);
        replica.setToolExecutionGuard(new online.yudream.base.infra.platform.plugin.service.QqSandboxAgentToolExecutionGuard());
        var sandbox = sandbox("native-write");
        var request = workspaceRequest("native-write", "write_file");
        var cards = new ArrayList<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult>();
        try (var scope = online.yudream.base.infra.platform.plugin.service.QqSandboxExecutionScope.open(sandbox)) {
            assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                    () -> replica.run(request, null, null, cards::add));
        }
        assertTrue(cards.stream().anyMatch(card -> "denied".equals(card.action())
                && "call-external".equals(card.payload().get("_toolCallId"))));
        assertTrue(sandbox.timeline().stream().anyMatch(event -> "tool.rejected".equals(event.action())));
        var selection = new AgentModelSelection(fake, GenerateOptions.builder().build());
        try (var agent = replica.buildAgent("workflow", selection)) {
            assertEquals("", agent.workspaceFor("1", "native-write").readManagedWorkspaceFileUtf8(
                    io.agentscope.core.agent.RuntimeContext.empty(), "native-guard.txt"));
        }
    }

    @Test
    void nativeWorkspaceReadRejectsSandboxThatClosesAfterAgentWasBuilt() {
        fake.toolName = "read_file";
        fake.toolInput = Map.of("path", "native-guard.txt");
        var replica = runtimeFor(workspace);
        replica.setToolExecutionGuard(new online.yudream.base.infra.platform.plugin.service.QqSandboxAgentToolExecutionGuard());
        var sandbox = sandbox("native-read");
        fake.beforeToolCall = sandbox::close;
        var cards = new ArrayList<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult>();
        try (var scope = online.yudream.base.infra.platform.plugin.service.QqSandboxExecutionScope.open(sandbox)) {
            assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                    () -> replica.run(workspaceRequest("native-read", "read_file"), null, null, cards::add));
        }
        assertTrue(cards.stream().anyMatch(card -> "denied".equals(card.action())));
    }

    private online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession sandbox(String id) {
        return online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession.create(id, "demo", "1", "2", "3", null,
                "4", "group", online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode.REAL, 60_000L, java.time.Instant.now());
    }

    @Test
    void boundSandboxActorKeepsNativeIdentityAndQuotaButMemoryUsesSessionIsolation() {
        var replica = runtimeFor(workspace);
        var sandbox = sandbox("bound-user-memory");
        replica.setInvocationContexts(new online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway() {
            @Override public online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot capture() {
                var active = online.yudream.base.infra.platform.plugin.service.QqSandboxExecutionScope.requireActive();
                return active == null ? null : new online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot(
                        active.userId(), active.connectionId(), active.channelId(), "message", "MENTION", "trace",
                        List.of(), List.of(), active.id());
            }
            @Override public Scope restore(online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot snapshot,
                                          Long nativeUserId, List<String> permissions) { throw new UnsupportedOperationException(); }
        });
        var quotaActor = new java.util.concurrent.atomic.AtomicReference<Long>();
        replica.setUsageQuotaGateway(new AiUsageQuotaGateway() {
            @Override public AiUsageReservation reserve(Long userId, long budget) {
                quotaActor.set(userId); return new AiUsageReservation("sandbox", userId, java.time.LocalDate.now(), budget);
            }
            @Override public void settle(AiUsageReservation reservation, AiUsage usage) { assertEquals(15, usage.totalTokens()); }
        });
        var profile = new java.util.concurrent.atomic.AtomicReference<online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile>();
        var selection = new AgentModelSelection(fake, GenerateOptions.builder().build());
        HarnessRunResult result;
        try (var scope = online.yudream.base.infra.platform.plugin.service.QqSandboxExecutionScope.open(sandbox);
             var execution = HarnessExecutionScope.open("sandbox-memory", null, profile::set)) {
            result = replica.run(request("sandbox-memory-a"), null, null, null);
            try (var agent = replica.buildAgent("general", selection)) {
                agent.workspaceFor("1", "sandbox-memory-a").getFilesystem().write(io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md", "模拟记忆");
                assertEquals("", agent.workspaceFor("1", "sandbox-memory-b").readManagedWorkspaceFileUtf8(io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md"));
            }
        }
        assertEquals(1L, quotaActor.get());
        assertEquals(1L, profile.get().nativeUserId());
        assertEquals(sandbox.id(), profile.get().invocationContext().sandboxId());
        assertEquals(result.nativeTurnId(), replica.inspect(request("sandbox-memory-a")).turnId(), "日志寻址不因工作区隔离改变");
        try (var normal = replica.buildAgent("general", selection)) {
            assertEquals("", normal.workspaceFor("1", "sandbox-memory-a").readManagedWorkspaceFileUtf8(io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md"));
            normal.workspaceFor("1", "normal-a").getFilesystem().write(io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md", "正常记忆");
            assertEquals("正常记忆", normal.workspaceFor("1", "normal-b").readManagedWorkspaceFileUtf8(io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md"));
        }
        try (var scope = online.yudream.base.infra.platform.plugin.service.QqSandboxExecutionScope.open(sandbox);
             var sandboxAgent = replica.buildAgent("general", selection)) {
            assertEquals("模拟记忆", sandboxAgent.workspaceFor("1", "sandbox-memory-a").readManagedWorkspaceFileUtf8(io.agentscope.core.agent.RuntimeContext.empty(), "MEMORY.md"));
        }
    }

    private HarnessRunRequest workspaceRequest(String sessionId, String tool) {
        return new HarnessRunRequest(1L, sessionId, "workflow", "提示词", "工作区操作", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), true, List.of("*"), List.of(), List.of(tool));
    }

    private static final String PROVIDERS_JSON = """
            [{"code":"test","name":"测试","type":"OPENAI_COMPATIBLE","baseUrl":"https://api.example.com/v1",
              "apiKey":"sk-test","defaultModel":"fake-model",
              "models":[{"code":"fake-model","name":"fake-model","model":"fake-model"}]}]
            """;

    /**
     * 记录型假模型：固定回复，记录每次调用的消息文本与工具清单；脚本耗尽时回退默认响应
     * （吸收框架后台记忆提炼等辅助调用）。
     */
    static class RecordingFakeModel implements Model {
        private final String reply;
        private final List<List<String>> calls = new CopyOnWriteArrayList<>();
        private final List<List<Msg>> callMessages = new CopyOnWriteArrayList<>();
        private volatile List<ToolSchema> lastTools = List.of();
        volatile String thinking;
        volatile boolean hang;
        volatile String toolName;
        volatile Map<String, Object> toolInput = Map.of();
        volatile Runnable beforeToolCall;

        RecordingFakeModel(String reply) {
            this.reply = reply;
        }

        @Override
        public reactor.core.publisher.Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            List<String> texts = new ArrayList<>();
            for (Msg message : messages) {
                texts.add((message.getRole() == MsgRole.SYSTEM ? "[system] " : "") + message.getTextContent());
            }
            calls.add(texts);
            callMessages.add(List.copyOf(messages));
            lastTools = tools == null ? List.of() : tools;
            if (toolName != null && calls.size() == 1) {
                if (beforeToolCall != null) beforeToolCall.run();
                return reactor.core.publisher.Flux.just(new ChatResponse("r", List.<ContentBlock>of(
                        io.agentscope.core.message.ToolUseBlock.builder().id("call-external").name(toolName).input(toolInput).build()),
                        new io.agentscope.core.model.ChatUsage(10, 5, 0.1), Map.of(), "tool_calls"));
            }
            List<ChatResponse> chunks = new ArrayList<>();
            if (thinking != null) chunks.add(new ChatResponse("r", List.<ContentBlock>of(
                    io.agentscope.core.message.ThinkingBlock.builder().thinking(thinking).build()), null, Map.of(), null));
            chunks.add(new ChatResponse("r", List.<ContentBlock>of(TextBlock.builder().text(reply.substring(0, 1)).build()),
                    null, Map.of(), null));
            chunks.add(new ChatResponse("r", List.<ContentBlock>of(TextBlock.builder().text(reply.substring(1)).build()),
                    new io.agentscope.core.model.ChatUsage(10, 5, 0.1), Map.of(), hang ? null : "stop"));
            var flux = reactor.core.publisher.Flux.fromIterable(chunks);
            return hang ? flux.concatWith(reactor.core.publisher.Flux.never()) : flux;
        }

        @Override
        public String getModelName() {
            return "fake-model";
        }

        List<String> firstCallTexts() {
            return callTexts(0);
        }

        List<String> callTexts(int index) {
            return calls.size() > index ? calls.get(index) : List.of();
        }

        List<ToolSchema> lastTools() {
            return lastTools;
        }
    }
}
