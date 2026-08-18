package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiWebPage;

/**
 * 网页抓取端口：URL → 清洗后的标题/正文/类型，用于网页剪藏与批量 URL 导入。
 */
public interface WikiWebPageFetcher {

    WikiWebPage fetch(String url);
}
