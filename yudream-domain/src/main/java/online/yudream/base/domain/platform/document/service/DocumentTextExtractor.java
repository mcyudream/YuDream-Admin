package online.yudream.base.domain.platform.document.service;

import online.yudream.base.domain.platform.document.valobj.DocumentSource;

public interface DocumentTextExtractor {

    String extract(DocumentSource source);
}
