package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;

/**
 * 嵌入 WikiSourceDO 的图片记录。
 */
@Data
public class WikiSourceImageDO {
    private Long fileObjectId;
    private int pageNumber;
    private int sequence;
    private String caption;
    private WikiCaptionStatus captionStatus;
    private String captionProviderCode;
    private String captionModelCode;
    private int width;
    private int height;
    private String contentType;
}
