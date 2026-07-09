package online.yudream.base.plugin.activityproof.application.dto;

import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;

public record ActivityProofDownloadDTO(
        String filename,
        String contentType,
        PluginStoredFile file
) {
}
