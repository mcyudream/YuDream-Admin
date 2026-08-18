package online.yudream.base.infra.platform.chat.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.chat.service.ChatDocumentTextExtractor;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.document.valobj.DocumentSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TikaChatDocumentTextExtractor implements ChatDocumentTextExtractor {

    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint"
    );

    private static final Set<String> PLAIN_TEXT_TYPES = Set.of("text/plain", "text/markdown");

    private final DocumentTextExtractor delegate;

    @Override
    public boolean supports(String contentType) {
        return DOCUMENT_TYPES.contains(normalize(contentType));
    }

    @Override
    public String extract(DocumentSource source) {
        if (source == null || !StringUtils.hasText(source.content())) {
            throw new BizException("附件内容不能为空");
        }
        String contentType = normalize(source.contentType());
        if (PLAIN_TEXT_TYPES.contains(contentType)) {
            return decodePlainText(source.content());
        }
        return delegate.extract(source);
    }

    private String decodePlainText(String content) {
        if (content.regionMatches(true, 0, "data:", 0, 5)) {
            int separator = content.indexOf(',');
            if (separator > 5) {
                String header = content.substring(5, separator);
                if (header.toLowerCase(Locale.ROOT).contains("base64")) {
                    return new String(Base64.getDecoder().decode(content.substring(separator + 1)), StandardCharsets.UTF_8);
                }
                return content.substring(separator + 1);
            }
        }
        try {
            return new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException ignored) {
            return content;
        }
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
