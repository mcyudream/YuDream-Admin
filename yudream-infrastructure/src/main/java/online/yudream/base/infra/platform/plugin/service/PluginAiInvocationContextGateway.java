package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway;
import online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSessionRepo;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PluginAiInvocationContextGateway implements AiInvocationContextGateway {
    private static final String SANDBOX_CONNECTION = "devtools-sandbox:";
    private final QqSandboxSessionRepo sandboxes;
    private final PluginAiToolRegistry registry;

    @Override public AiInvocationContextSnapshot capture() {
        var context = PluginAiToolExecutionScope.current();
        var sandbox = QqSandboxExecutionScope.requireActive();
        if (context == null && sandbox == null) return null;
        String connection = context == null ? sandbox.connectionId() : context.connectionId();
        String sandboxId = sandbox == null ? sandboxId(connection) : sandbox.id();
        if (sandboxId != null) requireSandbox(sandboxId, connection);
        List<String> allowed = context == null ? List.of() : context.allowedToolNames().contains("*")
                ? registry.tools().stream().map(tool -> tool.descriptor())
                .filter(tool -> tool.allowedTriggers().contains(context.trigger()) && context.hasPermission(tool.permissionCode())
                        && (tool.risk() == online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk.READ || context.grantsWriteTool(tool.name())))
                .map(tool -> tool.name()).distinct().sorted().toList() : context.allowedToolNames();
        return new AiInvocationContextSnapshot(context == null ? sandbox.userId() : context.platformUserId(), connection,
                context == null ? sandbox.channelId() : context.channelId(), context == null ? null : context.messageId(),
                context == null ? sandbox.scene() : context.trigger(), context == null ? null : context.traceId(), allowed,
                context == null ? List.of() : context.grantedWriteToolNames(), sandboxId);
    }

    @Override public Scope restore(AiInvocationContextSnapshot snapshot, Long nativeUserId, List<String> freshPermissions) {
        if (snapshot == null) return () -> { };
        String referencedSandbox = snapshot.sandboxId() == null ? sandboxId(snapshot.connectionId()) : snapshot.sandboxId();
        QqSandboxSession sandbox = referencedSandbox == null ? null : requireSandbox(referencedSandbox, snapshot.connectionId());
        if (sandbox != null && (!Objects.equals(sandbox.userId(), snapshot.platformUserId()) || !Objects.equals(sandbox.channelId(), snapshot.channelId())))
            throw new BizException("原 QQ 沙箱触发身份已变化，不能恢复 AI 工具");
        var previous = PluginAiToolExecutionScope.current();
        var sandboxScope = sandbox == null ? null : QqSandboxExecutionScope.open(sandbox);
        PluginAiToolExecutionScope.set(new PluginAiExecutionContext(nativeUserId, snapshot.platformUserId(), snapshot.connectionId(),
                snapshot.channelId(), snapshot.messageId(), snapshot.trigger(), snapshot.traceId(), freshPermissions,
                snapshot.allowedToolNames(), snapshot.grantedWriteToolNames()));
        return new Scope() {
            private boolean closed;
            @Override public void close() {
                if (closed) return;
                closed = true;
                try { if (sandboxScope != null) sandboxScope.close(); }
                finally { if (previous == null) PluginAiToolExecutionScope.clear(); else PluginAiToolExecutionScope.set(previous); }
            }
        };
    }
    private QqSandboxSession requireSandbox(String id, String connection) {
        var sandbox = sandboxes.findById(id).orElseThrow(() -> new BizException("原 QQ 沙箱已不存在或已过期，不能恢复到真实连接"));
        if (!sandbox.acceptsCaptures() || !Objects.equals(sandbox.connectionId(), connection))
            throw new BizException("原 QQ 沙箱已关闭或寻址不一致，不能恢复 AI 工具");
        return sandbox;
    }
    private static String sandboxId(String connection) {
        return connection != null && connection.startsWith(SANDBOX_CONNECTION) ? connection.substring(SANDBOX_CONNECTION.length()) : null;
    }
}
