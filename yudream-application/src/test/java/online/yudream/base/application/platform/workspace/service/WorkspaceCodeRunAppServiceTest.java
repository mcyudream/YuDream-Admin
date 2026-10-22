package online.yudream.base.application.platform.workspace.service;

import online.yudream.base.application.platform.chat.service.ChatWorkspaceTools;
import online.yudream.base.application.platform.workspace.cmd.WorkspaceRunCmd;
import online.yudream.base.application.platform.workspace.dto.WorkspaceRunResultDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.workspace.service.CodeRunnerGateway;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunRequest;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceCodeRunAppServiceTest {

    /** 记录请求并回放固定结果的运行器 */
    static final class FakeRunner implements CodeRunnerGateway {
        CodeRunRequest last;
        String unavailable;
        Map<String, byte[]> outputs = Map.of();

        public String unavailableReason() {
            return unavailable;
        }

        public Map<String, String> runtimes() {
            return Map.of("python", "3.12");
        }

        public CodeRunResult run(CodeRunRequest request) {
            last = request;
            return new CodeRunResult(0, false, 42, "ok", "", outputs, false);
        }
    }

    @SuppressWarnings("unchecked")
    private static WorkspaceCodeRunAppService service(CodeRunnerGateway runner) {
        ObjectProvider<CodeRunnerGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(runner);
        return new WorkspaceCodeRunAppService(provider);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void runsWithInputsFromScopedRootAndWritesOutputsWithoutOverwritingSources() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        f.service.writeText(7L, "apps/report/data/sales.csv", "a,b\n1,2\n", null);
        f.service.writeText(7L, "apps/report/data/notes.md", "notes", null);
        f.service.writeText(7L, "apps/report/scripts/report.py", "print(1)", null);
        f.service.writeText(7L, "private.md", "secret", null);
        WorkspaceAccess access = f.service.access(7L, "apps/report");
        FakeRunner runner = new FakeRunner();
        Map<String, byte[]> outputs = new LinkedHashMap<>();
        outputs.put("out/summary.txt", utf8("sum=3"));
        outputs.put("data/sales.csv", utf8("changed"));
        runner.outputs = outputs;

        WorkspaceRunResultDTO result = service(runner).run(access, new WorkspaceRunCmd("Python", "scripts/report.py", null,
                List.of("data"), List.of("--fast"), null, "results/run-1", 10));

        assertEquals("python", runner.last.language());
        assertEquals(java.util.Set.of("data/sales.csv", "data/notes.md", "scripts/report.py"), runner.last.files().keySet());
        assertEquals(10, runner.last.timeoutSeconds());
        assertEquals(List.of("results/run-1/out/summary.txt", "results/run-1/data/sales.csv"), result.written());
        assertEquals("a,b\n1,2\n", access.read("data/sales.csv", 100).content());
        assertEquals("sum=3", f.service.read(7L, "apps/report/results/run-1/out/summary.txt").content());
        assertEquals("AI", f.service.overview(7L).entries().stream().filter(e -> e.path().endsWith("summary.txt")).findFirst().orElseThrow().updatedBy());
    }

    @Test
    void defaultsOutputToTmpRunsAndRejectsBadRequests() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        WorkspaceAccess access = f.service.access(7L, "");
        FakeRunner runner = new FakeRunner();
        runner.outputs = Map.of("plot.txt", utf8("x"));
        WorkspaceRunResultDTO result = service(runner).run(access, new WorkspaceRunCmd("node", null, "console.log(1)", null, null, null, null, 999));
        assertTrue(result.outputDir().startsWith("tmp/runs/"));
        assertEquals(120, runner.last.timeoutSeconds());
        assertEquals("console.log(1)", runner.last.code());
        assertThrows(BizException.class, () -> service(runner).run(access, new WorkspaceRunCmd("ruby", null, "puts 1", null, null, null, null, null)));
        assertThrows(BizException.class, () -> service(runner).run(access, new WorkspaceRunCmd("python", null, null, null, null, null, null, null)));
        assertThrows(BizException.class, () -> service(runner).run(access, new WorkspaceRunCmd("python", null, "x", List.of(""), null, null, null, null)));
        assertThrows(BizException.class, () -> service(runner).run(access, new WorkspaceRunCmd("python", null, "x", null, null, null, "/", null)));
        assertThrows(BizException.class, () -> service(runner).run(access, new WorkspaceRunCmd("python", "../escape.py", null, null, null, null, null, null)));
    }

    @Test
    void unavailableRunnerIsReportedAndToolNeedsConfirmation() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        assertNotNull(service(null).unavailableReason());
        FakeRunner down = new FakeRunner();
        down.unavailable = "无法连接代码运行器";
        assertEquals("无法连接代码运行器", service(down).unavailableReason());
        assertThrows(BizException.class, () -> service(down).run(f.service.access(7L, ""), new WorkspaceRunCmd("python", null, "print(1)", null, null, null, null, null)));

        List<AiAgentTool> tools = ChatWorkspaceTools.createRunnable(f.service.access(7L, ""), service(new FakeRunner()));
        AiAgentTool run = tools.getFirst();
        assertEquals(ChatWorkspaceTools.RUN_TOOL, run.descriptor().name());
        assertEquals(ChatWorkspaceTools.RUN_PERMISSION_CODE, run.descriptor().permissionCode());
        assertEquals(AgentToolRisk.WRITE, run.risk());
        assertTrue(run.requiresConfirmation());
        var result = run.execute(new AiAgentToolCall(ChatWorkspaceTools.RUN_TOOL, Map.of("language", "python", "code", "print(1)")));
        assertEquals(0, result.payload().get("exitCode"));
        assertEquals("ok", result.payload().get("stdout"));
        assertEquals(List.of(), ChatWorkspaceTools.createRunnable(null, service(new FakeRunner())));
    }
}
