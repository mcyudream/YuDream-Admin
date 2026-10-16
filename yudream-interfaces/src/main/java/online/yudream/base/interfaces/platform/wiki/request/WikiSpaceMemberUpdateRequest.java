package online.yudream.base.interfaces.platform.wiki.request;

import lombok.Data;

import java.util.List;

/**
 * 调整空间成员授权请求。expectedVersion 为成员当前版本号，用于 CAS。
 */
@Data
public class WikiSpaceMemberUpdateRequest {
    private List<String> actions;
    private Integer expectedVersion;
}
