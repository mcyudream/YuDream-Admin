package online.yudream.base.plugin.projectprogress.application.dto;

import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;

public record ProjectProgressDownloadDTO(
        String filename,
        String contentType,
        PluginStoredFile file
) {
}
