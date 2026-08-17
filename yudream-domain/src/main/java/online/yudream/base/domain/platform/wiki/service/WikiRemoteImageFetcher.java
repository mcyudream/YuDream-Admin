package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiRemoteImage;

/**
 * 远程图片抓取端口：下载在线 Markdown 文档中引用的远程图片，用于资料源图片摄取。
 */
public interface WikiRemoteImageFetcher {

    WikiRemoteImage fetch(String url);
}
