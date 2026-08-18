package online.yudream.base.domain.platform.chat.valobj;

import java.util.List;

public record ChatCitation(
        String title,
        String path,
        String nodeId,
        String excerpt,
        String spaceSlug,
        String spaceName,
        String sourceUrl,
        List<Image> images
) {
    /** 引用页面中的相关图片（站内文件地址 + 说明） */
    public record Image(String url, String caption) {
    }
}
