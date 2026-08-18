package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 网络搜索 Provider 配置。
 *
 * @param providerCode tavily / serpapi / searxng
 * @param apiKey       Tavily / SerpApi 的 API Key（SearXNG 可为空）
 * @param instanceUrl  SearXNG 实例地址
 * @param engine       SerpApi 搜索引擎（google / bing 等）
 * @param category     SearXNG 搜索分类
 */
public record WikiWebSearchConfig(
        String providerCode,
        String apiKey,
        String instanceUrl,
        String engine,
        String category
) {
    public static WikiWebSearchConfig none() {
        return new WikiWebSearchConfig("", "", "", "", "");
    }

    public boolean enabled() {
        return providerCode != null && !providerCode.isBlank();
    }
}
