package online.yudream.base.application.platform.milky.sandbox.dto;

import java.time.Instant;
import java.util.Map;

public record QqSandboxTimelineEventDTO(long sequence, Instant timestamp, String phase, String action,
                                        String pluginCode, Map<String, Object> payload) { }
