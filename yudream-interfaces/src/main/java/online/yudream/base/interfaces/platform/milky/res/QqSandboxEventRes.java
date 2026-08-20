package online.yudream.base.interfaces.platform.milky.res;

import java.time.Instant;

public record QqSandboxEventRes(String event, String action, String module, String traceId,
                                Instant timestamp, Object payload) { }
