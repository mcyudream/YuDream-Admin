package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 远程图片抓取结果：Markdown 中引用的远程图片下载后的字节与元信息。
 */
public record WikiRemoteImage(
        byte[] content,
        String contentType,
        String fileName
) {
}
