package online.yudream.base.interfaces.platform.ai;

import cn.hutool.json.JSONUtil;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiWebAssemblerAguiTest {

    @Test
    void emitsStandardAguiToolLifecycleWithTheActualToolName() {
        var tool = new AiAgentToolResult(
                "cms.canvas.block.add",
                "add-html",
                "platform:ai:tool:cms-canvas-patch",
                "新区块已追加到画布",
                Map.of("htmlContent", "<section>new</section>")
        );

        var started = AiWebAssembler.toAguiToolStart("run-1", "run-1-tool-1", tool);
        var completed = AiWebAssembler.toAguiToolResult("run-1", "run-1-tool-1", tool);

        assertThat(started.getType()).isEqualTo("TOOL_CALL_START");
        assertThat(started.getToolCallName()).isEqualTo("cms.canvas.block.add");
        assertThat(completed.getType()).isEqualTo("TOOL_CALL_RESULT");
        assertThat(completed.getToolCallId()).isEqualTo("run-1-tool-1");
        assertThat(started.getThreadId()).isEqualTo("cms-builder");
        assertThat(started.getRunId()).isEqualTo("run-1");
        assertThat(started.getParentMessageId()).isEqualTo("assistant-run-1");
        assertThat(JSONUtil.parseObj(String.valueOf(completed.getContent())).getStr("toolName"))
                .isEqualTo("cms.canvas.block.add");
    }

    @Test
    void emitsAguiRunLifecycle() {
        var started = AiWebAssembler.toAguiRunStarted("run-1");

        assertThat(started.getType()).isEqualTo("RUN_STARTED");
        assertThat(started.getThreadId()).isEqualTo("cms-builder");
        assertThat(started.getRunId()).isEqualTo("run-1");
    }

    @Test
    void emitsARegisteredActivitySnapshotThenDeltaForTheSameRun() {
        var snapshot = AiWebAssembler.toAguiActivitySnapshot("run-1", "accepted", "连接模型");
        var delta = AiWebAssembler.toAguiActivityDelta("run-1", "heartbeat", "仍在生成");

        assertThat(snapshot.getActivityType()).isEqualTo("cms-progress");
        assertThat(snapshot.getThreadId()).isEqualTo("cms-builder");
        assertThat(snapshot.getRunId()).isEqualTo("run-1");
        assertThat(delta.getType()).isEqualTo("ACTIVITY_DELTA");
        assertThat(delta.getActivityType()).isEqualTo("cms-progress");
        assertThat(delta.getPatch()).isInstanceOf(List.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emitsStructuredAguiCardOnlyAsAnActivitySnapshot() {
        var card = AiWebAssembler.toAguiCardSnapshot("run-1", """
                {"title":"风险摘要","summary":"发现两项风险","tone":"warning",
                 "fields":[{"label":"高风险","value":"2"}],
                 "actions":[{"label":"查看详情","action":"open","value":"/risk"}]}
                """);

        assertThat(card.getType()).isEqualTo("ACTIVITY_SNAPSHOT");
        assertThat(card.getActivityType()).isEqualTo("agui-card");
        assertThat(card.getContent()).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) card.getContent()).containsEntry("title", "风险摘要");
        assertThat(card.getDelta()).isNull();
    }
}
