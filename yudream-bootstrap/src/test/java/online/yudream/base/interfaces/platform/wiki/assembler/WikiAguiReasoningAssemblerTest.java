package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WikiAguiReasoningAssemblerTest {

    @Test
    void thinkingEventsUseStableMessageIdAndFinalPayloadKeepsReasoning() {
        var start = WikiAguiWebAssembler.thinkingStart("trace");
        var first = WikiAguiWebAssembler.thinkingContent("trace", "分析一");
        var second = WikiAguiWebAssembler.thinkingContent("trace", "分析二");
        var end = WikiAguiWebAssembler.thinkingEnd("trace");
        var finished = WikiAguiWebAssembler.runFinished(
                "trace", new WikiChatResultDTO("答案", "分析一分析二", List.of()));

        assertThat(start.getType()).isEqualTo("THINKING_START");
        assertThat(first.getType()).isEqualTo("THINKING_CONTENT");
        assertThat(second.getType()).isEqualTo("THINKING_CONTENT");
        assertThat(end.getType()).isEqualTo("THINKING_END");
        assertThat(List.of(start, first, second, end)).extracting(item -> item.getMessageId())
                .containsOnly("thinking-trace");
        assertThat(first.getDelta()).isEqualTo("分析一");
        assertThat(finished.getResult()).isInstanceOfSatisfying(Map.class,
                payload -> assertThat(payload).containsEntry("reasoning", "分析一分析二"));
    }
}
