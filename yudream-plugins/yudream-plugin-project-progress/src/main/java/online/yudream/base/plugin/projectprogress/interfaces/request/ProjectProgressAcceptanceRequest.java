package online.yudream.base.plugin.projectprogress.interfaces.request;

public record ProjectProgressAcceptanceRequest(
        String reason,
        String toStatusCode
) {
}
