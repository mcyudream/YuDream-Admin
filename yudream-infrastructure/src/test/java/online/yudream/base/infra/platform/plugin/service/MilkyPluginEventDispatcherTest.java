package online.yudream.base.infra.platform.plugin.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MilkyPluginEventDispatcherTest {

    @Test
    void normalizesMilkyJoinNotificationFieldsForPlugins() {
        MilkyPluginEventDispatcher.GroupRequest request = MilkyPluginEventDispatcher.groupRequest(Map.of(
                "group_id", 1064685901L,
                "initiator_id", 3816679582L,
                "notification_seq", 1784279528159539L,
                "comment", "问题：物品聚合器的作用\n答案：垃圾桶"));

        assertNotNull(request);
        assertEquals("1064685901", request.groupId());
        assertEquals("3816679582", request.userId());
        assertEquals("1784279528159539", request.requestId());
        assertEquals("问题：物品聚合器的作用\n答案：垃圾桶", request.comment());
    }
}
