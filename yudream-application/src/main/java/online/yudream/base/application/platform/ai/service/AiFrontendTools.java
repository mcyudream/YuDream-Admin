package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.ai.dto.AiFrontendContextDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 每轮闭包工具：仅导航或聚焦已声明入口，绝不执行页面按钮或发送表单。 */
public final class AiFrontendTools {
    private AiFrontendTools() { }
    public static List<AiAgentTool> create(AiFrontendContextDTO context, AiRunAppService.RunEmitter emitter) {
        if (context == null) return List.of();
        Set<String> routes = (context.routes() == null ? List.<AiFrontendContextDTO.Route>of() : context.routes())
                .stream().map(AiFrontendContextDTO.Route::name).collect(Collectors.toUnmodifiableSet());
        List<AiAgentTool> tools = new java.util.ArrayList<>();
        if (!Boolean.FALSE.equals(context.navigationToolsEnabled()) && !routes.isEmpty()) tools.add(tool("ui.navigate", "定位功能页面", "打开用户已授权的可见功能页面。仅使用提供的 routeName。",
                "routeName", routes, emitter));
        boolean highlight = Boolean.TRUE.equals(context.highlightToolsEnabled())
                || (context.highlightToolsEnabled() == null && context.actions() != null && !context.actions().isEmpty());
        if (highlight) tools.add(tool("ui.highlight", "突出显示页面操作", "聚焦并高亮当前页面真实登记的操作；不会点击或提交。仅使用页面上下文或导航结果提供的 actionCode，导航后以新页面反馈为准。",
                "actionCode", Set.of(), emitter));
        if (Boolean.TRUE.equals(context.tourToolsEnabled()) && context.tours() != null && !context.tours().isEmpty()) {
            tools.add(tool("ui.tour", "播放页面新手引导", "在当前页面播放分步新手引导；仅展示与定位，不会点击或提交。仅使用提供的 tourCode。",
                    "tourCode", Set.copyOf(context.tours()), emitter));
        }
        return List.copyOf(tools);
    }
    private static AiAgentTool tool(String name, String title, String description, String argument,
                                   Set<String> allowed, AiRunAppService.RunEmitter emitter) {
        return new online.yudream.base.domain.platform.ai.service.AiExternalAgentTool() {
            public AiAgentToolDescriptor descriptor() {
                return new AiAgentToolDescriptor(name, title, description, "platform:chat:use", "使用 AI 助手",
                        "平台能力", "允许助手帮助定位可见功能入口", Map.of(argument,
                        "actionCode".equals(argument)
                                ? Map.of("type", "string", "pattern", "^[a-z][a-z0-9:._-]{1,179}$", "maxLength", 180, "description", title)
                                : Map.of("type", "string", "enum", allowed.stream().sorted().toList(), "description", title)));
            }
            public AgentToolRisk risk() { return AgentToolRisk.READ; }
            public AiAgentToolResult prepare(String requestId, AiAgentToolCall call) {
                Object raw = call.arguments() == null ? null : call.arguments().get(argument);
                String target = raw == null ? "" : String.valueOf(raw);
                if ("actionCode".equals(argument) ? !target.matches("^[a-z][a-z0-9:._-]{1,179}$") : !allowed.contains(target)) {
                    throw new BizException("助手只能定位本次声明的可见功能入口");
                }
                Map<String, Object> args = Map.of(argument, target);
                emitter.frontendRequest(requestId, name, args);
                return new AiAgentToolResult(name, "pending", "platform:chat:use",
                        "等待前端按当前权限执行已授权定位操作", Map.of("_toolCallId", requestId, "arguments", args));
            }
        };
    }
}
