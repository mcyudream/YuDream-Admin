package online.yudream.base.domain.platform.milky.model;

import online.yudream.base.domain.platform.milky.enumerate.OfficialQqBotIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialQqBotEventCatalogTest {

    @Test
    void resolvesOfficialNativeTypesIntoFilterableCategories() {
        assertEquals("QQ 群消息", OfficialQqBotEventCatalog.resolve("GROUP_MESSAGE_CREATE", "message_receive", "group").category());
        assertEquals("群普通消息", OfficialQqBotEventCatalog.resolve("GROUP_MESSAGE_CREATE", "message_receive", "group").label());
        assertEquals("QQ 单聊", OfficialQqBotEventCatalog.resolve("C2C_MESSAGE_CREATE", "message_receive", "friend").category());
        assertEquals("QQ 互动", OfficialQqBotEventCatalog.resolve("INTERACTION_CREATE", "button_click", "group").category());
        assertEquals("QQ 群管理", OfficialQqBotEventCatalog.resolve("GROUP_JOIN_REQUEST", "group_request", "group").category());
        assertEquals("QQ 频道", OfficialQqBotEventCatalog.resolve("AT_MESSAGE_CREATE", "message_receive", "channel").category());
        assertEquals("QQ 网关", OfficialQqBotEventCatalog.resolve("READY", "ready", null).category());
        assertEquals(OfficialQqBotIntent.GROUP_AND_C2C_EVENT,
                OfficialQqBotEventCatalog.resolve("GROUP_AT_MESSAGE_CREATE", null, "group").intent());
    }

    @Test
    void unknownEventsStillStayUnderQqModules() {
        OfficialQqBotEventCatalog.Kind kind = OfficialQqBotEventCatalog.resolve("SOME_NEW_EVENT", "some_new_event", "group");
        assertEquals("QQ 群消息", kind.category());
        assertEquals("SOME_NEW_EVENT", kind.nativeType());
        assertTrue(OfficialQqBotEventCatalog.LOG_MODULES.contains(kind.category()));
    }

    @Test
    void genericMessageReceiveUsesSceneWhenNativeTypeMissing() {
        assertEquals("QQ 单聊", OfficialQqBotEventCatalog.resolve(null, "message_receive", "friend").category());
        assertEquals("QQ 频道", OfficialQqBotEventCatalog.resolve(null, "message_receive", "channel").category());
        assertEquals("QQ 群消息", OfficialQqBotEventCatalog.resolve(null, "message_receive", "group").category());
    }
}
