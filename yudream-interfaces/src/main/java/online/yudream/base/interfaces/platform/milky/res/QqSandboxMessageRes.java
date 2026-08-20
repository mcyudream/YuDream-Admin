package online.yudream.base.interfaces.platform.milky.res;

import java.time.Instant;
import java.util.Map;

public record QqSandboxMessageRes(String messageId, String sessionId, String direction, String senderId,
                                  String senderName, String messageType, String content, Instant occurredAt,
                                  Map<String, Object> metadata) { }
