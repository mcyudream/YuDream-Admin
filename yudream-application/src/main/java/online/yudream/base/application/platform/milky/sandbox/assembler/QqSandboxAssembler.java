package online.yudream.base.application.platform.milky.sandbox.assembler;

import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxCreateCmd;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxMessageCmd;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxCaseDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxSessionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxTimelineEventDTO;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCase;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseSetup;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseStep;
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

    public static QqSandboxCaseDTO toCaseDTO(QqSandboxCase sandboxCase) {
        return new QqSandboxCaseDTO(sandboxCase.id(), sandboxCase.name(), sandboxCase.description(),
                sandboxCase.createdAt(), sandboxCase.updatedAt(), sandboxCase.setup(), sandboxCase.steps());
    }

    /** 用例初始参数映射回创建会话命令，超时沿用默认（由应用层兜底） */
    public static QqSandboxCreateCmd toCreateCmd(QqSandboxCaseSetup setup) {
        return new QqSandboxCreateCmd(setup.pluginCode(), setup.policyConnectionId(), setup.selfId(), setup.userId(),
                setup.nickname(), setup.channelId(), setup.scene(), setup.randomMode(), setup.forceUnbound(),
                setup.simulateRoles(), null);
    }

    public static QqSandboxMessageCmd toMessageCmd(QqSandboxCaseStep step) {
        return new QqSandboxMessageCmd(step.senderId(), step.nickname(), step.content(), step.mentionSelf(),
                step.mentions(), step.replyMessageId(), null);
    }
}
