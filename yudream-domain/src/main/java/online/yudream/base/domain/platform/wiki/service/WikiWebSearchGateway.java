package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiWebSearchConfig;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebSearchResult;

import java.util.List;

/**
 * 网络搜索端口（Tavily / SerpApi / SearXNG），用于 Deep Research。
 */
public interface WikiWebSearchGateway {

    List<WikiWebSearchResult> search(WikiWebSearchConfig config, String query, int limit);
}
