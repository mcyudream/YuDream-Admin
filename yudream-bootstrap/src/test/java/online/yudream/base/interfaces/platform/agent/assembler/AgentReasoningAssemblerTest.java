package online.yudream.base.interfaces.platform.agent.assembler;

import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentReasoningAssemblerTest {

    @Test
    void thinkingLifecycleAndFinalResponseKeepReasoning() {
        AgentRunDTO result = AgentRunDTO.builder()
                .content("答案")
                .reasoning("分析过程")
                .toolResults(List.of())
                .build();

        var start = AgentWebAssembler.toDebugThinkingStart("run");
        var content = AgentWebAssembler.toDebugThinkingContent("run", "分析过程");
        var end = AgentWebAssembler.toDebugThinkingEnd("run");
        var finished = AgentWebAssembler.toDebugRunFinished("run", result);

        assertThat(start.getType()).isEqualTo("THINKING_START");
        assertThat(content.getType()).isEqualTo("THINKING_CONTENT");
        assertThat(content.getDelta()).isEqualTo("分析过程");
        assertThat(end.getType()).isEqualTo("THINKING_END");
        assertThat(finished.getResult().getReasoning()).isEqualTo("分析过程");
        assertThat(AgentWebAssembler.toRes(result).getReasoning()).isEqualTo("分析过程");
    }
}
