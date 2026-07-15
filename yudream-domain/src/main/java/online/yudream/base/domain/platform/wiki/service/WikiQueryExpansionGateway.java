package online.yudream.base.domain.platform.wiki.service;

import java.util.List;

public interface WikiQueryExpansionGateway {
    List<String> expand(String providerCode, String modelCode, String query);
}
