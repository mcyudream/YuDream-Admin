package online.yudream.base.interfaces.platform.ai;

import online.yudream.base.interfaces.platform.ai.controller.AiController;
import online.yudream.base.interfaces.platform.ai.controller.AiRunController;
import online.yudream.base.interfaces.platform.ai.controller.CmsWorkspaceController;
import online.yudream.base.interfaces.platform.chat.controller.ChatController;
import online.yudream.base.interfaces.platform.wiki.controller.WikiCaptionController;
import online.yudream.base.interfaces.platform.wiki.controller.WikiChatController;
import online.yudream.base.interfaces.platform.wiki.controller.WikiDeepResearchController;
import online.yudream.base.interfaces.platform.wiki.controller.WikiLintController;
import online.yudream.base.interfaces.platform.wiki.controller.WikiPublicChatController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiProjectGateTest {
    @Test void disabledProjectRegistersNoAiEndpoint() {
        new ApplicationContextRunner()
                .withPropertyValues("yudream.platform.capabilities.ai.enabled=false")
                .withUserConfiguration(AiController.class, AiRunController.class, CmsWorkspaceController.class,
                        ChatController.class, WikiCaptionController.class,
                        WikiChatController.class, WikiDeepResearchController.class, WikiLintController.class, WikiPublicChatController.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AiController.class).doesNotHaveBean(AiRunController.class)
                            .doesNotHaveBean(CmsWorkspaceController.class)
                            .doesNotHaveBean(ChatController.class).doesNotHaveBean(WikiCaptionController.class)
                            .doesNotHaveBean(WikiChatController.class).doesNotHaveBean(WikiDeepResearchController.class)
                            .doesNotHaveBean(WikiLintController.class).doesNotHaveBean(WikiPublicChatController.class);
                });
    }
}
