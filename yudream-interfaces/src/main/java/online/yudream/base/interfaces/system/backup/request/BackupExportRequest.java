package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

import java.util.List;

/** 全量导出请求。 */
@Data
public class BackupExportRequest {
    /** 范围 tag 列表：system / plugin:{pluginCode}/{scopeCode}；为空默认 system。 */
    private List<String> scopeTags;
}
