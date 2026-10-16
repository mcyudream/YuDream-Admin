package online.yudream.base.interfaces.platform.wiki.request;

import lombok.Data;

/**
 * 空间封面更新请求。
 * <p>
 * coverType：NONE 清除、SEEDED 使用稳定 seed、UPLOAD 引用已上传的公开图片文件。
 */
@Data
public class WikiSpaceCoverRequest {
    private String coverType;
    /** 文件对象 ID（字符串，保持长 ID 精度）。 */
    private String fileObjectId;
    private String seed;
    private String alt;
}
