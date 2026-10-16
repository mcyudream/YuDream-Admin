package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 空间成员授权响应。
 */
@Data
@Builder
public class WikiSpaceMemberRes implements Serializable {
    private String id;
    private String spaceId;
    private String subjectType;
    private String subjectId;
    private String subjectName;
    private List<String> actions;
    private String createdByUserId;
    /** 当前版本号，调整授权时必须回传。 */
    private int version;
}
