package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;

/**
 * 从原始资料抽取出的单张图片及其视觉 caption。
 *
 * @param fileObjectId        图片上传到对象存储后的文件 ID（用于生成 /api/files/{id}/content 访问地址）
 * @param pageNumber          所在页码（从 1 开始；非 PDF 图片为 1）
 * @param sequence            同资料内的图片序号
 * @param caption             视觉模型生成的事实性描述
 * @param captionStatus       caption 生成状态
 * @param captionProviderCode caption 使用的视觉模型 Provider
 * @param captionModelCode    caption 使用的视觉模型
 * @param width               图片宽度（像素）
 * @param height              图片高度（像素）
 * @param contentType         图片 MIME 类型
 */
public record WikiSourceImage(
        Long fileObjectId,
        int pageNumber,
        int sequence,
        String caption,
        WikiCaptionStatus captionStatus,
        String captionProviderCode,
        String captionModelCode,
        int width,
        int height,
        String contentType
) {
}
