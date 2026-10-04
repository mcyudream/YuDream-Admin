package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoBaseStoreTest extends MongoStoreTestBase {

    private MongoBaseStore store;

    @BeforeEach
    void initStore() {
        store = new MongoBaseStore(mongo);
    }

    @Test
    void putThenGetRoundTripsValue() {
        Map<String, Object> data = Map.of("content", "# MEMORY\n", "isDir", false);
        store.put(List.of("agents", "a1", "users", "u1"), "MEMORY.md", data);

        StoreItem item = store.get(List.of("agents", "a1", "users", "u1"), "MEMORY.md");
        assertEquals("MEMORY.md", item.key());
        assertEquals("# MEMORY\n", item.value().get("content"));
        assertEquals(1, item.version());
    }

    @Test
    void getMissingKeyReturnsNull() {
        assertNull(store.get(List.of("ns"), "missing"));
    }

    @Test
    void putBumpsVersionOnExistingEntry() {
        List<String> ns = List.of("memory");
        store.put(ns, "a.md", Map.of("content", "v1"));
        store.put(ns, "a.md", Map.of("content", "v2"));

        assertEquals(2, store.get(ns, "a.md").version());
        assertEquals("v2", store.get(ns, "a.md").value().get("content"));
    }

    @Test
    void putIfVersionOnlyWritesWhenVersionMatches() {
        List<String> ns = List.of("memory");
        store.put(ns, "a.md", Map.of("content", "v1"));

        assertFalse(store.putIfVersion(ns, "a.md", Map.of("content", "conflict"), 99));
        assertTrue(store.putIfVersion(ns, "a.md", Map.of("content", "v2"), 1));
        assertEquals("v2", store.get(ns, "a.md").value().get("content"));
        assertEquals(2, store.get(ns, "a.md").version());
    }

    @Test
    void searchReturnsNamespacePrefixInKeyOrderWithPaging() {
        store.put(List.of("agents", "a1"), "memory/b.md", Map.of("i", 2));
        store.put(List.of("agents", "a1"), "memory/a.md", Map.of("i", 1));
        store.put(List.of("agents", "a1"), "MEMORY.md", Map.of("i", 0));
        store.put(List.of("agents", "other"), "memory/z.md", Map.of("i", 9));

        List<StoreItem> page = store.search(List.of("agents", "a1"), 0, 2);
        assertEquals(2, page.size());
        assertEquals("MEMORY.md", page.get(0).key());
        assertEquals("memory/a.md", page.get(1).key());

        List<StoreItem> secondPage = store.search(List.of("agents", "a1"), 2, 10);
        assertEquals(1, secondPage.size());
        assertEquals("memory/b.md", secondPage.get(0).key());
    }

    @Test
    void deleteRemovesExactKeyOnly() {
        store.put(List.of("ns"), "a.md", Map.of());
        store.put(List.of("ns"), "a.md.bak", Map.of());

        store.delete(List.of("ns"), "a.md");
        assertNull(store.get(List.of("ns"), "a.md"));
        assertNotSame(null, store.get(List.of("ns"), "a.md.bak"));
    }
}
