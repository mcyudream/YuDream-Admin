package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.assembler.AgentWorkflowCheckpointAssembler;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.application.platform.ai.service.AiRunContinuation;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentWorkflowCheckpoint;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.platform.agent.repo.AgentWorkflowCheckpointRepo;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${yudream.platform.capabilities.ai.enabled:false} && ${yudream.platform.capabilities.agent.enabled:false}")
public class WorkflowAiRunContinuation implements AiRunContinuation {
    private final AgentWorkflowCheckpointRepo checkpoints;
    private final AgentApplicationRepo applications;
    private final AgentRuntimeApplicationRegistry runtimeApplications;
    private final AgentWorkflowRuntimeService workflow;
    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModules;
    private final ObjectMapper json;

    @Override public Optional<List<AiAgentTool>> restoreTools(AiRun run, Set<String> permissions, AiRunAppService.RunEmitter emitter) {
        var checkpoint = owned(run);
        if (checkpoint.isEmpty()) return Optional.empty();
        var app = unchangedApplication(checkpoint.get());
        return Optional.of(workflow.restoreCheckpointTools(checkpoint.get(), app,
                AgentWorkflowCheckpointAssembler.command(checkpoint.get(), permissions, json)));
    }

    @Override public Optional<Object> continueAfterNative(AiRun run, HarnessRunResult result,
            Set<String> permissions, AiRunAppService.RunEmitter emitter) {
        var checkpoint = owned(run);
        if (checkpoint.isEmpty()) return Optional.empty();
        var app = unchangedApplication(checkpoint.get());
        if ("suspended".equals(result.status())) {
            var next = checkpoint.get().appendNativeSuspension(result);
            if (next != checkpoint.get()) checkpoints.save(next);
            return Optional.empty();
        }
        if (!"completed".equals(result.status())) return Optional.empty();
        var config = capabilityModules.findByCode("ai").filter(online.yudream.base.domain.platform.capability.aggregate.CapabilityModule::enabled)
                .map(module -> module.getConfig() == null ? java.util.Map.<String, String>of() : module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力未启用"));
        var completed = workflow.continueAfterNative(checkpoint.get(), app,
                AgentWorkflowCheckpointAssembler.command(checkpoint.get(), permissions, json), config, result,
                node -> emitter.progress("agent-node", node), emitter::content, emitter::reasoning, emitter::tool,
                progress -> emitter.progress(progress.action(), progress));
        return Optional.of(AgentRunDTO.builder().content(completed.content()).reasoning(completed.reasoning())
                .toolResults(completed.toolResults()).usage(completed.usage()).build());
    }

    @Override public void cancel(AiRun run) {
        checkpoints.find(run.getId()).filter(checkpoint -> Objects.equals(checkpoint.userId(), run.getUserId()))
                .ifPresent(checkpoint -> checkpoints.cancel(run.getId()));
    }

    private Optional<AgentWorkflowCheckpoint> owned(AiRun run) {
        var checkpoint = checkpoints.find(run.getId());
        if (checkpoint.isEmpty() || Set.of("COMPLETED", "CANCELLED").contains(checkpoint.get().status())) return Optional.empty();
        if (!Objects.equals(checkpoint.get().userId(), run.getUserId())) throw new BizException("无权访问此工作流检查点");
        if (!"WAITING".equals(checkpoint.get().status()) || checkpoint.get().frame().suspendedNative() == null) {
            throw new BizException("业务节点结果尚未核实，不能自动重跑已开始的操作");
        }
        return checkpoint;
    }

    private AgentApplication unchangedApplication(AgentWorkflowCheckpoint checkpoint) {
        capabilities.ensureEnabled("agent", "Agent 工作流");
        capabilities.ensureEnabled("ai", "AI 助手");
        var command = AgentWorkflowCheckpointAssembler.command(checkpoint, Set.of(), json);
        var local = applications.findByCode(checkpoint.applicationCode());
        var runtime = runtimeApplications.findByCode(checkpoint.applicationCode());
        AgentApplication app;
        if (command.getApplicationId() != null) {
            app = applications.findById(command.getApplicationId()).orElseThrow(() -> new BizException("原 Agent 应用已删除"));
            if (app.isPluginOverride() && runtime.isEmpty()) throw new BizException("原工作流所属插件未加载");
        } else if (runtime.isPresent()) {
            app = local.filter(AgentApplication::isPluginOverride)
                    .filter(value -> value.getStatus() == AgentApplicationStatus.PUBLISHED || value.getStatus() == AgentApplicationStatus.DISABLED)
                    .orElse(runtime.get());
        } else {
            app = local.filter(value -> !value.isPluginOverride()).orElseThrow(() -> new BizException("原 Agent 应用当前不可用"));
        }
        if (app.getStatus() == AgentApplicationStatus.DISABLED
                || !Objects.equals(app.getCode(), checkpoint.applicationCode())
                || !Objects.equals(app.getWorkflowJson(), checkpoint.workflowJson())
                || !Objects.equals(app.getSystemPrompt(), checkpoint.applicationSystemPrompt())) {
            throw new BizException("Agent 工作流或提示策略已变化，请取消旧运行后重新发起");
        }
        return app;
    }
}
