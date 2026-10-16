package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 新增空间成员授权请求。subjectType 为 USER/ROLE，subjectId 为字符串长 ID，actions 为动作码列表。
 */
@Data
public class WikiSpaceMemberSaveRequest {
    @NotBlank(message = "成员主体类型不能为空")
    private String subjectType;
    @NotBlank(message = "成员主体不能为空")
    private String subjectId;
    private List<String> actions;
}
