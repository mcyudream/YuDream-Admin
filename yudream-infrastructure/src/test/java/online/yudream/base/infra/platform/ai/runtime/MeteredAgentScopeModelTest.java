package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeteredAgentScopeModelTest {
    @Test
    void cumulativeProviderSnapshotsAreCountedOncePerActualModelCallIncludingAuxiliaryCalls() {
        Model delegate = new Model() {
            @Override public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(new ChatResponse("r", List.of(), new ChatUsage(10, 1, 0.1), Map.of(), null),
                        new ChatResponse("r", List.of(), new ChatUsage(10, 5, 0.1), Map.of(), "stop"));
            }
            @Override public String getModelName() { return "test"; }
            @Override public boolean supportsNativeStructuredOutput() { return true; }
            @Override public boolean supportsNativeStructuredOutputWithTools() { return false; }
            @Override public int getContextWindowSize() { return 32000; }
        };
        HarnessRunEvents events = new HarnessRunEvents(null, null, null);
        Model model = new MeteredAgentScopeModel(delegate, events);
        model.stream(List.of(), List.of(), null).blockLast();
        model.stream(List.of(), List.of(), null).blockLast();
        assertEquals(30, events.usage().totalTokens());
        assertEquals(20, events.usage().promptTokens());
        assertEquals(10, events.usage().completionTokens());
        assertEquals(32000, model.getContextWindowSize());
        assertTrue(model.supportsNativeStructuredOutput());
        assertEquals(false, model.supportsNativeStructuredOutputWithTools());
    }
}
