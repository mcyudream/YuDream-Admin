package online.yudream.base.infra.platform.milky.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import online.yudream.base.domain.platform.milky.event.MilkyEventPublished;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.model.OfficialQqBotEventCatalog;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilkyEventSystemLoggerTest {

    @Test
    void logsSubscribedEventsWithCategoryModule() {
        Logger logger = (Logger) LoggerFactory.getLogger(MilkyEventSystemLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("native_type", "GROUP_MESSAGE_CREATE");
            data.put("message_scene", "group");
            data.put("peer_id", "group-1");
            data.put("sender_id", "user-1");
            data.put("message_seq", "msg-1");
            data.put("mention_self", true);
            data.put("raw_message", "你好机器人");
            new MilkyEventSystemLogger().onEvent(new MilkyEventPublished(88L,
                    new MilkyModels.Event(1L, "bot", "message_receive", data)));
            ILoggingEvent event = appender.list.getFirst();
            assertEquals(Level.INFO, event.getLevel());
            assertEquals("QQ 群消息", event.getMDCPropertyMap().get(MilkyEventSystemLogger.LOG_MODULE_MDC));
            assertTrue(event.getFormattedMessage().contains("GROUP_MESSAGE_CREATE"));
            assertTrue(event.getFormattedMessage().contains("群普通消息"));
            assertTrue(event.getFormattedMessage().contains("你好机器人"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void unknownEventsStillStayUnderQqModules() {
        Logger logger = (Logger) LoggerFactory.getLogger(MilkyEventSystemLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new MilkyEventSystemLogger().onEvent(new MilkyEventPublished(1L,
                    new MilkyModels.Event(1L, "bot", "forum_thread_create",
                            Map.of("native_type", "FORUM_THREAD_CREATE", "guild_id", "g1"))));
            ILoggingEvent event = appender.list.getFirst();
            assertEquals(OfficialQqBotEventCatalog.CATEGORY_FORUM,
                    event.getMDCPropertyMap().get(MilkyEventSystemLogger.LOG_MODULE_MDC));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void previewTruncatesLongContent() {
        assertEquals("-", MilkyEventSystemLogger.preview("  "));
        assertEquals("hello", MilkyEventSystemLogger.preview("hello"));
        String longText = "x".repeat(90);
        assertEquals("x".repeat(80) + "...", MilkyEventSystemLogger.preview(longText));
    }

    @Test
    void contentReadsSegmentsWhenRawMessageMissing() {
        assertEquals("abc", MilkyEventSystemLogger.content(Map.of(
                "segments", List.of(Map.of("type", "text", "data", Map.of("text", "abc"))))));
    }
}
