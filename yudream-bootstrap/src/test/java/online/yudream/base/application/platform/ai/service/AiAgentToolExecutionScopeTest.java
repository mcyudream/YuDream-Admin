package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具执行作用域：工具白名单 + 本次运行发起人（实例级授权工具的主体来源）。
 * 内层作用域未显式声明发起人时必须继承外层，否则 LLM 节点内的单工具执行会丢掉主体。
 */
class AiAgentToolExecutionScopeTest {

    private static final AiAgentTool STUB = new AiAgentTool() {
        @Override
        public AiAgentToolDescriptor descriptor() {
            return new AiAgentToolDescriptor("stub", "占位工具", "测试用", "test:tool",
                    "占位", "测试", "占位工具", Map.of());
        }

        @Override
        public AgentToolRisk risk() {
            return AgentToolRisk.READ;
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            return new AiAgentToolResult("stub", "noop", "test:tool", "ok", Map.of());
        }
    };

    @Test
    void actingUserIsVisibleInsideScopeAndNestedScopesInheritIt() {
        assertThat(AiAgentToolExecutionScope.currentUserId()).isNull();

        try (AiAgentToolExecutionScope outer = AiAgentToolExecutionScope.open(Set.of("stub"), 42L)) {
            assertThat(AiAgentToolExecutionScope.currentUserId()).isEqualTo(42L);

            try (AiAgentToolExecutionScope inner = AiAgentToolExecutionScope.open(List.of(STUB))) {
                assertThat(AiAgentToolExecutionScope.currentUserId()).isEqualTo(42L);
                assertThat(AiAgentToolExecutionScope.current()).containsExactly("stub");
            }

            assertThat(AiAgentToolExecutionScope.currentUserId()).isEqualTo(42L);
        }

        assertThat(AiAgentToolExecutionScope.currentUserId()).isNull();
    }

    @Test
    void explicitActingUserOverridesInheritedOne() {
        try (AiAgentToolExecutionScope outer = AiAgentToolExecutionScope.open(Set.of("stub"), 42L);
             AiAgentToolExecutionScope inner = AiAgentToolExecutionScope.open(Set.of("stub"), 7L)) {
            assertThat(AiAgentToolExecutionScope.currentUserId()).isEqualTo(7L);
        }
    }

    @Test
    void scopeWithoutActingUserKeepsIdentityUnknownForFailClosedConsumers() {
        try (AiAgentToolExecutionScope scope = AiAgentToolExecutionScope.open(List.of(STUB))) {
            assertThat(AiAgentToolExecutionScope.currentUserId()).isNull();
        }
    }
}
