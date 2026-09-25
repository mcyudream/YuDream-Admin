package online.yudream.base.interfaces.system.backup.res;

import lombok.Builder;
import lombok.Data;

/** 远端归档文件响应。 */
@Data
@Builder
public class RemoteArchiveRes {
    private String name;
    private Long size;
    private Long modifiedAtMillis;
}
