package online.yudream.base.application.platform.wiki.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;

import java.io.Serializable;

/**
 * 空间封面更新输入。SEEDED 只接受稳定 seed，UPLOAD 只接受文件对象引用，
 * 归属与可公开读取由应用服务校验，客户端不能自定义展示内容。
 */
@Data
public class WikiSpaceCoverCmd implements Serializable {
    private Long spaceId;
    private WikiSpaceCoverType coverType;
    private Long coverFileObjectId;
    private String coverSeed;
    private String coverAlt;
}
