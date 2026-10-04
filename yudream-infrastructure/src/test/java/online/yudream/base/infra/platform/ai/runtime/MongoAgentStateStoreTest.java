package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoAgentStateStoreTest extends MongoStoreTestBase {

    private MongoAgentStateStore store;

    @BeforeEach
    void initStore() {
        store = new MongoAgentStateStore(mongo);
    }

    @Test
    void savesAndReloadsAgentStateBySession() {
        AgentState state = AgentState.builder()
                .sessionId("session-1")
                .summary("压缩摘要")
                .build();
        store.save("agent-1", "user-1", "session-1", state);

        Optional<AgentState> reloaded = store.get("agent-1", "user-1", "session-1", AgentState.class);
        assertTrue(reloaded.isPresent());
        assertEquals("压缩摘要", reloaded.get().getSummary());
        assertEquals("session-1", reloaded.get().getSessionId());
    }

    @Test
    void anonymousUserIsNormalizedIntoSameBucket() {
        AgentState state = AgentState.builder().sessionId("session-1").build();
        store.save("agent-1", null, "session-1", state);
        store.save("agent-1", "", "session-1", state);

        assertTrue(store.get("agent-1", null, "session-1", AgentState.class).isPresent());
        assertTrue(store.exists("agent-1", "__anon__"));
        assertEquals(1, mongo.rows.size());
    }

    @Test
    void getReturnsEmptyWhenNothingStored() {
        assertTrue(store.get("agent-1", "user-1", "missing", AgentState.class).isEmpty());
        assertFalse(store.exists("agent-1", "user-1"));
    }

    @Test
    void deletesBySessionAndByUser() {
        AgentState state = AgentState.builder().sessionId("session-1").build();
        store.save("agent-1", "user-1", "session-1", state);
        store.save("agent-1", "user-1", "session-2", state);

        store.delete("agent-1", "user-1", "session-1");
        assertTrue(store.get("agent-1", "user-1", "session-1", AgentState.class).isEmpty());
        assertTrue(store.get("agent-1", "user-1", "session-2", AgentState.class).isPresent());

        store.delete("agent-1", "user-1");
        assertFalse(store.exists("agent-1", "user-1"));
    }

    @Test
    void listsAllSessionIdsOfAgent() {
        AgentState state = AgentState.builder().sessionId("s1").build();
        store.save("agent-1", "user-1", "s1", state);
        store.save("agent-1", "user-2", "s2", state);

        var sessionIds = store.listSessionIds("agent-1");
        assertEquals(2, sessionIds.size());
        assertTrue(sessionIds.contains("s1"));
        assertTrue(sessionIds.contains("s2"));
    }

    @Test
    void savesAndReloadsStateLists() {
        Marker state = new Marker();
        state.setValue("a");
        Marker other = new Marker();
        other.setValue("b");
        store.save("agent-1", "user-1", "session-1", List.of(state, other));

        List<Marker> reloaded = store.getList("agent-1", "user-1", "session-1", Marker.class);
        assertEquals(2, reloaded.size());
        assertEquals("a", reloaded.get(0).getValue());
        assertEquals("b", reloaded.get(1).getValue());
    }

    public static class Marker implements State {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
