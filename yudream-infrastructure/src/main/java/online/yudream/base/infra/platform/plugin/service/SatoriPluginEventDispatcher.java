package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.satori.event.SatoriEventPublished;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SatoriPluginEventDispatcher {
    private final JarPluginRuntimeGateway pluginRuntimeGateway;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final SettingRepo settingRepo;

    @EventListener
    public void dispatch(SatoriEventPublished published) {
        var event = published.event();
        String userId = event.user() == null ? (event.message() == null || event.message().user() == null ? null : event.message().user().id()) : event.user().id();
        PluginEvent pluginEvent = new PluginEvent(
                event.sn(), event.type(), event.platform(), event.selfId(),
                event.channel() == null ? null : event.channel().id(),
                event.message() == null ? null : event.message().content(),
                event.button() == null ? null : event.button().id(),
                event.argv() == null ? null : event.argv().name(),
                event.referrer(), event.extensionType(), event.extensionData(), String.valueOf(published.connectionId()), event.selfId(),
                event.message() == null ? null : event.message().id());
        pluginEvent = new PluginEvent(pluginEvent.sequence(), pluginEvent.type(), pluginEvent.platform(), userId,
                pluginEvent.channelId(), pluginEvent.content(), pluginEvent.buttonId(), pluginEvent.command(),
                pluginEvent.referrer(), pluginEvent.nativeType(), pluginEvent.nativeData(), pluginEvent.connectionId(), pluginEvent.selfId(), pluginEvent.messageId());
        pluginRuntimeGateway.publishSatoriEvent(pluginEvent);
        ParsedCommand command = parse(pluginEvent.content(), pluginEvent.command());
        if (command != null) {
            User user = userId == null ? null : userRepo.findByQQ(userId).orElse(null);
            if (requiresBoundQq() && user == null && !"绑定".equals(command.name())) return;
            pluginRuntimeGateway.publishCommand(pluginEvent, command.name(), command.arguments(), user == null ? null : user.getId(),
                    permission -> hasPermission(user, permission));
        }
    }

    private boolean requiresBoundQq() {
        return settingRepo.findByKey("plugin.qq-binding.require-bound-qq")
                .map(online.yudream.base.domain.system.setting.aggregate.Setting::getValue)
                .map(Boolean::parseBoolean).orElse(false);
    }

    private boolean hasPermission(User user, String permission) {
        if (user == null) return false;
        return roleRepo.findByIds(user.getRoles().stream().map(role -> role.getValue()).toList()).stream()
                .filter(role -> role.getStatus() == online.yudream.base.domain.system.user.enumerate.RoleStatus.ACTIVE)
                .anyMatch(role -> role.hasPermission(permission));
    }

    private ParsedCommand parse(String content, String nativeCommand) {
        if (nativeCommand != null && !nativeCommand.isBlank()) return new ParsedCommand(nativeCommand.trim(), java.util.List.of());
        if (content == null) return null;
        String value = content.trim();
        if (!(value.startsWith("/") || value.startsWith("!"))) return null;
        String[] parts = value.substring(1).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return null;
        return new ParsedCommand(parts[0], java.util.Arrays.stream(parts).skip(1).toList());
    }

    private record ParsedCommand(String name, java.util.List<String> arguments) { }
}
