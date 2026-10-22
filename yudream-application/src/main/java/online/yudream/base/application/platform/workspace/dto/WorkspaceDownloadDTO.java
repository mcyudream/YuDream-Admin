package online.yudream.base.application.platform.workspace.dto;

import java.io.InputStream;

public record WorkspaceDownloadDTO(String fileName, String contentType, long size, InputStream inputStream) {
}
