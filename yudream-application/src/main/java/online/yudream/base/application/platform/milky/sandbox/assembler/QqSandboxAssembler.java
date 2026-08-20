package online.yudream.base.application.platform.milky.sandbox.assembler;

import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxSessionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxTimelineEventDTO;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxTimelineEvent;

public final class QqSandboxAssembler {
    private QqSandboxAssembler() { }

    public static QqSandboxSessionDTO toDTO(QqSandboxSession session) {
        return new QqSandboxSessionDTO(session.id(), session.pluginCode(), session.connectionId(),
                session.policyConnectionId(), session.selfId(),
                session.userId(), session.nickname(), session.channelId(), session.scene(), session.randomMode(),
                session.timeoutMillis(),
                session.status(), session.createdAt(),
                session.timeline().stream().map(QqSandboxAssembler::toDTO).toList());
    }

    public static QqSandboxTimelineEventDTO toDTO(QqSandboxTimelineEvent event) {
        return new QqSandboxTimelineEventDTO(event.sequence(), event.timestamp(), event.phase(), event.action(),
                event.pluginCode(), event.payload());
    }
}
