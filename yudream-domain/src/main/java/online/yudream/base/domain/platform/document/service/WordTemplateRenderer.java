package online.yudream.base.domain.platform.document.service;

import online.yudream.base.domain.platform.document.valobj.RenderedDocument;

import java.io.InputStream;
import java.util.Map;

public interface WordTemplateRenderer {

    RenderedDocument render(InputStream templateInputStream, Map<String, String> data);
}
