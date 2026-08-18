package online.yudream.base.interfaces.platform.chat.assembler;

import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAguiWebAssemblerTest {

    @Test
    void buildsStableThinkingLifecycleEventsInProtocolOrder() {
        List<AguiStreamEventRes> events = List.of(
                ChatAguiWebAssembler.thinkingStart("run-1"),
                ChatAguiWebAssembler.thinkingContent("run-1", "分析一"),
                ChatAguiWebAssembler.thinkingContent("run-1", "\n分析二"),
                ChatAguiWebAssembler.thinkingEnd("run-1")
        );

        assertThat(events).extracting(AguiStreamEventRes::getType).containsExactly(
                "THINKING_TEXT_MESSAGE_START",
                "THINKING_TEXT_MESSAGE_CONTENT",
                "THINKING_TEXT_MESSAGE_CONTENT",
                "THINKING_TEXT_MESSAGE_END"
        );
        assertThat(events).extracting(AguiStreamEventRes::getMessageId)
                .containsOnly("thinking-run-1");
        assertThat(events).extracting(AguiStreamEventRes::getRole)
                .containsOnly("assistant");
        assertThat(events.get(1).getDelta()).isEqualTo("分析一");
        assertThat(events.get(2).getDelta()).isEqualTo("\n分析二");
    }
}
