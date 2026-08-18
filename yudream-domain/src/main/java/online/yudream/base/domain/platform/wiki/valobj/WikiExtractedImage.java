package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 文档图片抽取端口的输出：一张尚未写入对象存储的原始图片。
 *
 * @param pageNumber  页码（从 1 开始）
 * @param contentType MIME 类型
 * @param fileName    建议文件名（含扩展名）
 * @param content     图片字节
 * @param width       宽度（像素）
 * @param height      高度（像素）
 */
public record WikiExtractedImage(
        int pageNumber,
        String contentType,
        String fileName,
        byte[] content,
        int width,
        int height
) {
}
