package online.yudream.base.application.platform.chat.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.chat.service.ChatDocumentTextExtractor;
import online.yudream.base.domain.platform.document.valobj.DocumentSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ChatAttachmentAppService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_DOCUMENTS = Set.of(
            "application/pdf", "text/plain", "text/markdown",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint");

    private final CapabilityAppService capabilities;
    private final FileAppService fileAppService;
    private final ChatDocumentTextExtractor textExtractor;

    @Transactional
    public ChatAttachmentDTO upload(InputStream inputStream, String originalName, String contentType,
                                    long size, Long uploaderId) {
        capabilities.ensureEnabled("chat", "AI 助手");
        if (inputStream == null || size <= 0) {
            throw new BizException("附件不能为空");
        }
        String normalized = normalize(contentType);
        if (size > MAX_BYTES) {
            throw new BizException("附件大小不能超过 10 MB");
        }
        if (!ALLOWED_IMAGES.contains(normalized) && !ALLOWED_DOCUMENTS.contains(normalized)) {
            throw new BizException("不支持的附件类型：" + normalized);
        }
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        }
        catch (Exception e) {
            throw new BizException("附件读取失败");
        }
        if (bytes.length == 0) {
            throw new BizException("附件不能为空");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BizException("附件大小不能超过 10 MB");
        }
        FileObjectDTO file = fileAppService.upload(
                new ByteArrayInputStream(bytes),
                originalName,
                contentType,
                bytes.length,
                "chat",
                uploaderId,
                false);
        if (isImage(normalized)) {
            return new ChatAttachmentDTO(
                    String.valueOf(file.getId()),
                    file.getOriginalName(),
                    file.getContentType(),
                    file.getSize(),
                    "IMAGE",
                    file.getUrl(),
                    null,
                    dataUrl(normalized, bytes));
        }
        if (textExtractor.supports(normalized)) {
            String extracted = textExtractor.extract(DocumentSource.base64(
                    Base64.getEncoder().encodeToString(bytes),
                    normalized,
                    originalName));
            return new ChatAttachmentDTO(
                    String.valueOf(file.getId()),
                    file.getOriginalName(),
                    file.getContentType(),
                    file.getSize(),
                    "DOCUMENT",
                    file.getUrl(),
                    extracted,
                    null);
        }
        return new ChatAttachmentDTO(
                String.valueOf(file.getId()),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                "FILE",
                file.getUrl(),
                null,
                null);
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    private String dataUrl(String contentType, byte[] bytes) {
        String mime = StringUtils.hasText(contentType) ? contentType : "image/png";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String normalize(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String base = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }
}
