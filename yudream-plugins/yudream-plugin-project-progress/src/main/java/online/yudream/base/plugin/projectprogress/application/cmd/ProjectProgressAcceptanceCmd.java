package online.yudream.base.plugin.projectprogress.application.cmd;

public record ProjectProgressAcceptanceCmd(
        String reason,
        String toStatusCode
) {
}
