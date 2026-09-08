package online.yudream.base.plugin.spi.system.mail;

import java.util.List;

/** Read-only query for a host-managed inbound mailbox. */
public record PluginInboundMailQuery(
        String mailboxId,
        String verificationCode,
        List<String> allowedFromDomains,
        List<String> requiredKeywords,
        long receivedAfter,
        long expiresAt
) {
    public PluginInboundMailQuery {
        allowedFromDomains = allowedFromDomains == null ? List.of() : List.copyOf(allowedFromDomains);
        requiredKeywords = requiredKeywords == null ? List.of() : List.copyOf(requiredKeywords);
    }
}
