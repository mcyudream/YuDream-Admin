package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

import java.util.List;

/** 全量导出请求。 */
@Data
public class BackupExportRequest {
    /** 范围 tag 列表：system / plugin:{pluginCode}/{scopeCode}；为空默认 system。 */
    private List<String> scopeTags;
    /** 可选异地目标编码；填写时导出后立即推送到该目标（无需计划）。 */
    private String targetCode;
}
