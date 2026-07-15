package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiGraphRelation;
import java.util.List;

public interface WikiGraphExtractionGateway {
    List<WikiGraphRelation> extract(String providerCode, String modelCode, String title, String markdown);
}
