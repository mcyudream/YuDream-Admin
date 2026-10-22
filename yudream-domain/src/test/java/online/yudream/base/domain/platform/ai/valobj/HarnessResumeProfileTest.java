package online.yudream.base.domain.platform.ai.valobj;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HarnessResumeProfileTest {
    private HarnessResumeProfile profile(List<String> tools, boolean exact, Long actor) {
        return new HarnessResumeProfile("provider", "model", "提示", true, tools, exact, actor, true);
    }

    @Test
    void continuationKeepsPreviouslyObservedSchemaUntilTheNextModelCall() {
        var captured = profile(List.of("read", "apply"), true, 7L);
        assertSame(captured, profile(List.of("apply", "read"), false, 7L).retainCapturedScope(captured));
    }

    @Test
    void changedScopeOrActorNeverInheritsAuthorization() {
        var captured = profile(List.of("read"), true, 7L);
        var wider = profile(List.of("read", "apply"), false, 7L);
        assertSame(wider, wider.retainCapturedScope(captured));
        var otherActor = profile(List.of("read"), false, 8L);
        assertSame(otherActor, otherActor.retainCapturedScope(captured));
        assertFalse(profile(List.of("read"), false, 7L).retainCapturedScope(null).exactToolScopeCaptured());
    }

    @Test
    void newObservedSchemaReplacesThePreviousCapture() {
        var next = profile(List.of("read"), true, 7L);
        assertSame(next, next.retainCapturedScope(profile(List.of("read", "apply"), true, 7L)));
    }
}
