package online.yudream.base.interfaces.platform.satori.assembler;

import online.yudream.base.application.platform.satori.cmd.SatoriMediaSendCmd;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class SatoriMediaSendWebAssembler {
    private SatoriMediaSendWebAssembler() {
    }

    public static SatoriMediaSendCmd toCmd(Long connectionId, String platform, String userId, String channelId, String content,
                                           MultipartFile[] files) throws IOException {
        List<SatoriMediaSendCmd.Attachment> attachments = files == null ? List.of() : Arrays.stream(files)
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> toAttachment(file)).toList();
        return SatoriMediaSendCmd.builder().connectionId(connectionId).platform(platform).userId(userId).channelId(channelId)
                .content(content == null ? "" : content).attachments(attachments).build();
    }

    private static SatoriMediaSendCmd.Attachment toAttachment(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            return SatoriMediaSendCmd.Attachment.builder().filename(filename == null || filename.isBlank() ? "attachment" : filename)
                    .contentType(file.getContentType()).content(file.getBytes()).build();
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传附件失败", exception);
        }
    }
}
