package {{PACKAGE}}.bootstrap;

import online.yudream.base.plugin.spi.annotation.PluginCommand;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.command.PluginCommandContext;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;

import java.util.Map;

@PluginSpec(
        code = {{ENTRY_CLASS}}.CODE,
        name = "{{CODE}}",
        version = "{{VERSION}}",
        description = "{{DESCRIPTION}}"
)
public class {{ENTRY_CLASS}} implements YuDreamPlugin {

    public static final String CODE = "{{CODE}}";

    @Override
    public void onEnable(PluginContext context) {
        // 在此通过 context.registerXxx(...) 注册 HTTP 端点、菜单、Agent 工具等运行时贡献；
        // 禁用/卸载时宿主按注册句柄统一回收，无需手工反注册。
    }

    /** 自检指令：/{{CODE}} 回复 pong，用于验证加载、指令解析与消息链路。 */
    @PluginCommand(code = "{{CODE}}.ping", command = "{{CODE}}", name = "自检", description = "回复 pong，验证插件链路", allowAnonymous = true)
    public void ping(PluginCommandContext command, PluginContext context) {
        var event = command.event();
        String messageId = event.messageId();
        Map<String, Object> referrer = messageId == null || messageId.isBlank() ? Map.of() : Map.of("message_id", messageId);
        context.framework().messaging().send(new PluginMessageRequest(
                event.connectionId(), event.platform(), event.selfId(), event.channelId(),
                new PluginMessageContent(PluginMessageContent.Type.TEXT, "pong", null, referrer)));
    }
}
