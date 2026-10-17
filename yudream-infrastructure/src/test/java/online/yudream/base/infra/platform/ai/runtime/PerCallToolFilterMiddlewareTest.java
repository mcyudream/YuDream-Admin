package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.ToolSchema;
import online.yudream.base.domain.platform.agent.valobj.AgentToolPermissions;
import online.yudream.base.domain.platform.ai.valobj.AiToolLoadState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统工具按模块可见：按需模块加载前不进入模型视野，未加载工具的调用在执行层被拒绝。
 */
class PerCallToolFilterMiddlewareTest {

    private static final Map<String, String> PERMISSIONS = Map.of(
            "wiki_search", "",
            "cms_canvas_patch", "",
            "load_tools", "");

    @Test
    void 按需模块加载前后可见性变化() {
        AiToolLoadState state = state();
        RuntimeContext context = context(state);

        assertEquals(List.of("wiki_search", "load_tools"), visibleNames(context));

        assertTrue(state.load("cms"));
        assertEquals(List.of("wiki_search", "cms_canvas_patch", "load_tools"), visibleNames(context));
    }

    @Test
    void 元工具与常驻模块始终可见() {
        RuntimeContext context = context(state());
        List<String> names = visibleNames(context);
        assertTrue(names.contains("load_tools"));
        assertTrue(names.contains("wiki_search"));
    }

    @Test
    void 未加载模块的工具调用被拒绝() {
        AiToolLoadState state = state();
        RuntimeContext context = context(state);
        PerCallToolFilterMiddleware middleware = new PerCallToolFilterMiddleware(code -> true);

        var error = assertThrows(RuntimeException.class, () -> middleware
                .onActing(null, context, new ActingInput(List.of(ToolUseBlock.builder()
                        .id("call-1").name("cms_canvas_patch").input(Map.of()).build())),
                        input -> reactor.core.publisher.Flux.empty())
                .blockLast());
        assertTrue(error.getMessage().contains("未授权"), error.getMessage());

        state.load("cms");
        middleware.onActing(null, context, new ActingInput(List.of(ToolUseBlock.builder()
                        .id("call-2").name("cms_canvas_patch").input(Map.of()).build())),
                input -> reactor.core.publisher.Flux.empty()).blockLast();
    }

    @Test
    void 恢复续跑状态不做按需过滤() {
        AiToolLoadState resuming = new AiToolLoadState(false, "load_tools", Set.of("wiki_search"),
                Map.of("cms", List.of("cms_canvas_patch"), "wiki", List.of("wiki_search")));
        RuntimeContext context = context(resuming);
        assertEquals(List.of("wiki_search", "cms_canvas_patch", "load_tools"), visibleNames(context));
    }

    private AiToolLoadState state() {
        return new AiToolLoadState(true, "load_tools", Set.of("wiki_search"),
                Map.of("cms", List.of("cms_canvas_patch"), "wiki", List.of("wiki_search")));
    }

    private RuntimeContext context(AiToolLoadState state) {
        return RuntimeContext.builder()
                .userId("tester")
                .sessionId("session")
                .put(AgentToolCatalog.class, new AgentToolCatalog(true, PERMISSIONS, Set.of()))
                .put(AgentToolPermissions.class, AgentToolPermissions.explicit(List.of()))
                .put(AiToolLoadState.class, state)
                .build();
    }

    private List<String> visibleNames(RuntimeContext context) {
        PerCallToolFilterMiddleware middleware = new PerCallToolFilterMiddleware(code -> true);
        List<String> reported = new java.util.ArrayList<>();
        RuntimeContext withObserver = RuntimeContext.builder()
                .userId("tester")
                .sessionId("session")
                .put(AgentToolCatalog.class, context.get(AgentToolCatalog.class))
                .put(AgentToolPermissions.class, context.get(AgentToolPermissions.class))
                .put(AiToolLoadState.class, context.get(AiToolLoadState.class))
                .put(AgentToolSchemaObserver.class, new AgentToolSchemaObserver(reported::addAll))
                .build();
        ReasoningInput input = new ReasoningInput(List.of(), schemas(), null);
        middleware.onReasoning(null, withObserver, input, value -> {
            assertEquals(reported, value.tools().stream().map(ToolSchema::getName).toList());
            return reactor.core.publisher.Flux.empty();
        }).blockLast();
        return reported;
    }

    private List<ToolSchema> schemas() {
        Map<String, ToolSchema> schemas = new LinkedHashMap<>();
        for (String name : List.of("wiki_search", "cms_canvas_patch", "load_tools")) {
            schemas.put(name, ToolSchema.builder().name(name).description(name).parameters(Map.of()).build());
        }
        return List.copyOf(schemas.values());
    }
}
