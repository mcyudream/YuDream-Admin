package online.yudream.base.domain.platform.chat.service;

import online.yudream.base.domain.platform.document.valobj.DocumentSource;

public interface ChatDocumentTextExtractor {

    boolean supports(String contentType);

    String extract(DocumentSource source);
}
