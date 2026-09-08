package online.yudream.base.application.platform.devtools.cmd;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量重载或批量重启开发模式插件。codes 为空时处理全部已登记开发项目。
 */
@Data
public class PluginDevBatchReloadCmd {

    /** 目标插件编码；空列表表示全部已登记开发项目 */
    private List<String> codes = new ArrayList<>();
}
