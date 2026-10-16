package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 空间成员授权视图。subjectName 为展示用名称快照，权限判定只依据 subjectType + subjectId。
 */
@Data
@Builder
public class WikiSpaceMemberDTO {
    private String id;
    private String spaceId;
    /** USER / ROLE */
    private String subjectType;
    private String subjectId;
    private String subjectName;
    private List<String> actions;
    private String createdByUserId;
    /** 当前版本号，更新授权时必须回传做 CAS。 */
    private int version;
}
