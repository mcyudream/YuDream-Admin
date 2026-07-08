package online.yudream.base.plugin.activityproof.application.dto;

public record ActivityProofDependencyDTO(
        boolean minecraftReady,
        boolean studentInfoReady,
        boolean wordTemplateReady
) {
}
