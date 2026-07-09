package online.yudream.base.plugin.spi.system.mail;

import java.util.List;

public record PluginMailMessage(
        String from,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String text,
        String html
) {

    public PluginMailMessage {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        bcc = bcc == null ? List.of() : List.copyOf(bcc);
    }

    public static PluginMailMessage text(List<String> to, String subject, String text) {
        return new PluginMailMessage(null, to, List.of(), List.of(), subject, text, null);
    }

    public static PluginMailMessage html(List<String> to, String subject, String html) {
        return new PluginMailMessage(null, to, List.of(), List.of(), subject, null, html);
    }
}
