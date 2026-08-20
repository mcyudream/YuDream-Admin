package online.yudream.base.application.platform.milky.sandbox.port;

import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxMessageCmd;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface QqSandboxRuntimeGateway {
    CompletionStage<Void> dispatch(QqSandboxSession session, QqSandboxMessageCmd message);

    default void cancel(QqSandboxSession session) {
        if (session != null) session.close();
    }
}
