package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentWorkflowValueResolverTest {
    private final AgentWorkflowValueResolver resolver = new AgentWorkflowValueResolver(new ObjectMapper());

    @Test
    void renderShouldTreatMissingMapFieldAsEmptyValue() {
        AgentWorkflowContext context = new AgentWorkflowContext("输入");
        context.record("intent", online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult
                .output(Map.of("route", "chat"))
                .withVariables(Map.of("intent", Map.of("route", "chat"))));

        String rendered = resolver.render("群聊主题：{{intent.topic}}\n建议风格：{{intent.style}}", context);

        assertThat(rendered).isEqualTo("群聊主题：\n建议风格：");
    }

    @Test
    void renderSupportsSafeNavigationForMissingWorkflowOutput() {
        AgentWorkflowContext context = new AgentWorkflowContext("输入");

        assertThat(resolver.render("主题：{{intent?.topic ?: ''}}", context)).isEqualTo("主题：");
    }

    @Test
    void renderShouldStillRejectMalformedExpressions() {
        AgentWorkflowContext context = new AgentWorkflowContext("输入");

        assertThatThrownBy(() -> resolver.render("主题：{{intent.topic", context))
                .hasMessageContaining("模板表达式无效");
    }
}
