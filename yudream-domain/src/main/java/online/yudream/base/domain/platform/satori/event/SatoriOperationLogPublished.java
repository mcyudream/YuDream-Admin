package online.yudream.base.domain.platform.satori.event;

import online.yudream.base.domain.platform.satori.aggregate.SatoriOperationLog;

/** Published after a sanitized Satori operation log is persisted. */
public record SatoriOperationLogPublished(SatoriOperationLog log) {
}
