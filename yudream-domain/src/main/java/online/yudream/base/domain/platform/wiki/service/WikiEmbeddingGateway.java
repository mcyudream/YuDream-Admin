package online.yudream.base.domain.platform.wiki.service;

import java.util.List;

public interface WikiEmbeddingGateway {
    List<List<Float>> embed(String providerCode, String modelCode, List<String> texts);
}
