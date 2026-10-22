package online.yudream.base.interfaces.platform.chat.res;

public record WorkspaceFileContentRes(String path, String encoding, String content, boolean truncated, long size,
                                      String contentType, int revision) {
}
