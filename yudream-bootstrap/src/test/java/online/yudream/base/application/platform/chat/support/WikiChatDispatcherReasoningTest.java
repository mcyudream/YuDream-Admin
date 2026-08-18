package online.yudream.base.application.platform.chat.support;

import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.application.platform.wiki.service.WikiChatAppService;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WikiChatDispatcherReasoningTest {

    @Test
    void forwardsReasoningSeparatelyWithoutCreatingActivities() {
        WikiChatAppService wiki = mock(WikiChatAppService.class);
        when(wiki.chatStreamBySlug(eq("docs"), eq("question"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    invocation.<java.util.function.Consumer<String>>getArgument(4).accept("分析一");
                    invocation.<java.util.function.Consumer<String>>getArgument(4).accept("\n分析二");
                    return WikiChatResultDTO.builder()
                            .answer("回答")
                            .citations(List.of())
                            .usage(AiUsage.empty())
                            .build();
                });
        List<String> reasoning = new ArrayList<>();
        List<Object> activities = new ArrayList<>();
        ChatDispatchContext context = new ChatDispatchContext(
                "question", null, null, null, "docs", List.of(), List.of(), List.of(),
                ignored -> { }, reasoning::add, ignored -> { }, activities::add);

        ChatDispatchResult result = new WikiChatDispatcher(wiki).dispatch(context);

        assertThat(reasoning).containsExactly("分析一", "\n分析二");
        assertThat(activities).isEmpty();
        assertThat(result.content()).isEqualTo("回答");
    }
}
