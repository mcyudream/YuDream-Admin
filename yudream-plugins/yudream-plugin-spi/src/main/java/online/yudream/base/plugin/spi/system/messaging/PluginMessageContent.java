package online.yudream.base.plugin.spi.system.messaging;

import java.util.List;
import java.util.Map;

public record PluginMessageContent(
        Type type,
        String content,
        List<Attachment> attachments,
        Map<String, Object> referrer,
        List<Button> buttons
) {
    public enum Type { TEXT, MARKDOWN, HTML, IMAGE, AUDIO, VIDEO, FILE, COMPOSITE }

    public PluginMessageContent {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        referrer = referrer == null ? Map.of() : Map.copyOf(referrer);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }

    /**
     * 四参数构造保持对旧插件源码兼容，默认不带交互按钮。
     */
    public PluginMessageContent(Type type, String content, List<Attachment> attachments, Map<String, Object> referrer) {
        this(type, content, attachments, referrer, List.of());
    }

    /**
     * 返回附带交互按钮的副本，其余字段保持不变。
     */
    public PluginMessageContent withButtons(List<Button> extra) {
        return new PluginMessageContent(type, content, attachments, referrer, extra);
    }

    /**
     * 消息底部交互按钮，仅在支持键盘消息的连接（官方 QQ 机器人）上原生生效，
     * 其余协议自动降级忽略并在 {@link PluginMessageResult#degraded()} 标记。
     * {@code enter=true} 表示指令按钮：点击后直接以 {@code data} 作为消息发出（如 {@code /菜单}）；
     * {@code enter=false} 表示回调按钮：点击后平台回调 {@code id}，宿主以 button_click 事件路由给插件。
     */
    public record Button(String id, String label, String data, boolean enter) {
        public static Button command(String id, String label, String commandText) {
            return new Button(id, label, commandText, true);
        }

        public static Button callback(String id, String label, String data) {
            return new Button(id, label, data, false);
        }
    }

    public record Attachment(String url, String title, String contentType) {
    }
}
