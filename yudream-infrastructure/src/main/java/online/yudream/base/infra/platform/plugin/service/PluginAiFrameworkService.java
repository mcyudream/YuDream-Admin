package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatRequest;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatResponse;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class PluginAiFrameworkService implements PluginAiService {
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectProvider<AiGenerationGateway> gatewayProvider;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PluginAiToolRegistry pluginAiToolRegistry;
    private final AiProviderConfigParser providerConfigParser;
    private final AgentAppService agentAppService;

    @Override
    public List<online.yudream.base.plugin.spi.system.ai.PluginAiAgentOption> agents() {
        return agentAppService.publishedApplications().stream()
                .map(item -> new online.yudream.base.plugin.spi.system.ai.PluginAiAgentOption(
                        item.getCode(), item.getName(), item.getDescription()
                ))
                .toList();
    }

    @Override
    public CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            var executionContext = withPermissions(request);
            PluginAiToolExecutionScope.set(executionContext);
            try {
                return agentResponse(agentAppService.runByCode(
                        agentCode, agentCommand(request, executionContext)
                ));
            }
            finally {
                PluginAiToolExecutionScope.clear();
            }
        });
    }

    @Override
    public CompletionStage<PluginAiChatResponse> runAgentStream(
            String agentCode,
            PluginAiChatRequest request,
            Consumer<String> onDelta
    ) {
        return CompletableFuture.supplyAsync(() -> {
            var executionContext = withPermissions(request);
            PluginAiToolExecutionScope.set(executionContext);
            try {
                return agentResponse(agentAppService.debugByCode(
                        agentCode, agentCommand(request, executionContext), null, onDelta, null
                ));
            }
            finally {
                PluginAiToolExecutionScope.clear();
            }
        });
    }

    private AgentRunCmd agentCommand(
            PluginAiChatRequest request,
            online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext executionContext
    ) {
        AgentRunCmd command = new AgentRunCmd();
        command.setInput(request.userPrompt());
        command.setRuntimeSystemPrompt(request.systemPrompt());
        command.setProviderCode(request.providerCode());
        command.setModelCode(request.modelCode());
        command.setRuntimeToolCallingEnabled(request.toolCallingEnabled());
        command.setHistory(request.history().stream()
                .map(item -> new AiChatMessage(item.role(), item.content()))
                .toList());
        command.setPermissionCodes(executionContext == null ? List.of() : executionContext.permissions());
        command.setPermissionContextExplicit(true);
        return command;
    }

    private PluginAiChatResponse agentResponse(
            online.yudream.base.application.platform.agent.dto.AgentRunDTO result
    ) {
        return new PluginAiChatResponse(
                result.getContent(),
                result.getToolResults().stream()
                        .map(item -> new online.yudream.base.plugin.spi.system.ai.PluginAiToolResult(
                                item.action(), item.message(), item.payload()
                        ))
                        .toList()
        );
    }

    @Override
    public List<online.yudream.base.plugin.spi.system.ai.PluginAiProviderOption> providers() {
        Map<String, String> config = capabilityModuleRepo.findByCode("ai").map(item -> item.getConfig() == null ? Map.<String, String>of() : item.getConfig()).orElse(Map.of());
        return providerConfigParser.parse(config).stream().filter(item -> item.enabled()).map(item -> new online.yudream.base.plugin.spi.system.ai.PluginAiProviderOption(item.code(), item.name(), item.models().stream().filter(model -> "chat".equalsIgnoreCase(model.kind())).map(model -> new online.yudream.base.plugin.spi.system.ai.PluginAiModelOption(model.optionCode(), model.name())).toList())).toList();
    }

    @Override
    public List<online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor> tools() {
        return pluginAiToolRegistry.tools().stream().map(online.yudream.base.plugin.spi.system.ai.PluginAiTool::descriptor)
                .filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!capabilityModuleRepo.findByCode("ai").map(item -> Boolean.TRUE.equals(item.getEnabled())).orElse(false)) {
                throw new BizException("AI 能力未启用");
            }
            AiGenerationGateway gateway = gatewayProvider.getIfAvailable();
            if (gateway == null) {
                throw new BizException("AI 生成服务不可用");
            }
            List<AiChatMessage> history = request.history().stream()
                    .map(item -> new AiChatMessage(item.role(), item.content())).toList();
            Map<String, String> config = capabilityModuleRepo.findByCode("ai")
                    .map(item -> item.getConfig() == null ? Map.<String, String>of() : item.getConfig()).orElse(Map.of());
            PluginAiToolExecutionScope.set(withPermissions(request));
            try {
                var result = gateway.generate(new AiGenerationRequest(request.systemPrompt(), request.userPrompt(), null,
                        request.providerCode(), request.modelCode(), config, history, request.toolCallingEnabled()));
                return new PluginAiChatResponse(result.summary(), List.of());
            } finally { PluginAiToolExecutionScope.clear(); }
        });
    }

    private online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext withPermissions(PluginAiChatRequest request) {
        var context = request.executionContext();
        if (context == null || context.userId() == null) return context;
        List<String> permissions = userRepo.findById(context.userId()).map(user -> roleRepo.findByIds(user.getRoles().stream()
                .map(item -> item.getValue()).toList()).stream().flatMap(role -> role.getPermissions().stream())
                .map(item -> item.getCode()).distinct().toList()).orElse(List.of());
        return new online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext(context.userId(), context.platformUserId(), context.connectionId(), context.channelId(), context.messageId(), context.trigger(), context.traceId(), permissions, context.allowedToolNames());
    }
}
