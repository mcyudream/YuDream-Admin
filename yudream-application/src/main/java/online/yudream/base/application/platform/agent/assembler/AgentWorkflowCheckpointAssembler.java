package online.yudream.base.application.platform.agent.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentWorkflowCheckpoint;
import online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentWorkflowCheckpointAssembler {
    private AgentWorkflowCheckpointAssembler() { }
    public static AgentWorkflowCheckpoint capture(String id, AgentApplication application, AgentRunCmd command,
                                                   AgentWorkflowRunState state, AgentWorkflowFrame frame, ObjectMapper json) {
        Map<String, Object> safe = json.convertValue(command, new com.fasterxml.jackson.core.type.TypeReference<>() { });
        safe.remove("permissionCodes"); safe.remove("permissionContextExplicit");
        if (application.getId() != null) safe.put("applicationId", String.valueOf(application.getId()));
        return new AgentWorkflowCheckpoint(id, command.getUserId() == null ? 0L : command.getUserId(),
                application.getCode(), application.getWorkflowJson(), application.getSystemPrompt(), safe,
                state.runKey(), frame, state.modelInvocations(), state.reasoning(), state.toolResults(), state.usage(),
                frame.suspendedNative() == null ? "RUNNING" : "WAITING", Instant.now());
    }
    public static AgentRunCmd command(AgentWorkflowCheckpoint checkpoint, Set<String> permissions, ObjectMapper json) {
        var cmd = json.convertValue(checkpoint.command(), AgentRunCmd.class);
        cmd.setPermissionCodes(List.copyOf(permissions));
        cmd.setPermissionContextExplicit(true);
        return cmd;
    }
}
